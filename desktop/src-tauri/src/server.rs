use std::process::Child;
use std::sync::Mutex;
use tauri::Manager;

pub struct ServerProcess(Mutex<Option<Child>>);

impl ServerProcess {
    pub fn new() -> Self {
        ServerProcess(Mutex::new(None))
    }
}

#[allow(dead_code)]
fn wait_for_server(max_ms: u64) -> bool {
    let start = std::time::Instant::now();
    while start.elapsed().as_millis() < max_ms as u128 {
        if let Ok(stream) = std::net::TcpStream::connect("127.0.0.1:9847") {
            drop(stream);
            return true;
        }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
    false
}

fn shutdown_via_http() {
    use std::io::Write;
    if let Ok(mut stream) = std::net::TcpStream::connect_timeout(
        &"127.0.0.1:9847".parse().unwrap(),
        std::time::Duration::from_millis(500),
    ) {
        let _ = stream.set_write_timeout(Some(std::time::Duration::from_millis(500)));
        let _ = stream
            .write_all(b"POST /shutdown HTTP/1.0\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n");
    }
}

pub fn kill_existing_server(child: &mut Option<Child>) {
    shutdown_via_http();
    std::thread::sleep(std::time::Duration::from_millis(400));

    if let Some(mut c) = child.take() {
        let _ = c.kill();
        std::thread::sleep(std::time::Duration::from_millis(200));
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        let _ = std::process::Command::new("taskkill")
            .args(["/F", "/T", "/IM", "nest-music-server.exe"])
            .creation_flags(CREATE_NO_WINDOW)
            .output();
        if let Ok(out) = std::process::Command::new("netstat")
            .args(["-ano"])
            .creation_flags(CREATE_NO_WINDOW)
            .output()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                // :9847 = the Python server itself; :4416 = its bgutil PO-token Node child.
                // If the Python side was force-killed (its /shutdown never ran), the Node child
                // is orphaned and keeps a lock on node.exe — which then makes the NSIS updater
                // fail with "Error opening file for writing: ...\node.exe". Kill whatever still
                // listens on either port, targeted (won't touch the user's own node processes).
                if (line.contains(":9847") || line.contains(":4416")) && line.contains("LISTENING") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if let Some(pid) = parts.last() {
                        let _ = std::process::Command::new("taskkill")
                            .args(["/F", "/T", "/PID", pid])
                            .creation_flags(CREATE_NO_WINDOW)
                            .output();
                    }
                }
            }
        }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }

    // Linux: no taskkill equivalent is guaranteed to exist, but a sidecar that outlived us
    // (the app was SIGKILLed, so the child.kill() above never ran) keeps :9847 bound and the
    // freshly spawned server cannot start — the UI then sits there with no backend. Clean up
    // the leftovers by scanning /proc, which needs no extra tools or crates.
    #[cfg(target_os = "linux")]
    {
        kill_orphaned_server_processes();
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
}

/// SIGTERM any leftover sidecar (and its bgutil node child) found through `/proc`.
///
/// Only processes whose `argv[0]` is the frozen server itself, or `node` running the
/// PO-token generator, are touched — the user's own node/server processes are never
/// matched. `kill(1)` is used instead of the `libc` crate to avoid a new dependency
/// (and the Cargo.lock churn that comes with it); coreutils is present on every
/// desktop distribution that can run this app.
#[cfg(target_os = "linux")]
fn kill_orphaned_server_processes() {
    const SIDECAR_NAME: &str = "nest-music-server";

    let own_pid = std::process::id();
    let entries = match std::fs::read_dir("/proc") {
        Ok(e) => e,
        Err(_) => return,
    };

    for entry in entries.flatten() {
        // /proc/<pid> entries are the only numeric ones.
        let Some(pid) = entry.file_name().to_str().and_then(|s| s.parse::<u32>().ok()) else {
            continue;
        };
        if pid == own_pid || pid <= 1 {
            continue;
        }
        // NUL-separated argv; unreadable for processes owned by other users, which is fine —
        // a sidecar we spawned is always ours to signal.
        let Ok(raw) = std::fs::read(entry.path().join("cmdline")) else { continue };
        let argv: Vec<String> = raw
            .split(|b| *b == 0)
            .filter(|s| !s.is_empty())
            .map(|s| String::from_utf8_lossy(s).into_owned())
            .collect();
        let Some(argv0) = argv.first() else { continue };
        let prog = argv0.rsplit('/').next().unwrap_or(argv0.as_str());

        // The PyInstaller onefile bootloader runs as a parent+child pair, both with argv[0]
        // pointing at the frozen server, so a name match catches both halves.
        let is_sidecar = prog == SIDECAR_NAME;
        // The PO-token generator is `node .../potgen/server/build/main.js`; orphaned it holds
        // :4416 and the next sidecar cannot mint tokens.
        let is_potgen = prog == "node"
            && argv.iter().skip(1).any(|a| a.contains("potgen") && a.ends_with("main.js"));
        if !is_sidecar && !is_potgen {
            continue;
        }

        eprintln!("[server] terminating leftover process {pid} ({prog})");
        let pid_str = pid.to_string();
        let _ = std::process::Command::new("kill")
            .args(["-TERM", pid_str.as_str()])
            .output();
    }
}

/// Every directory the frozen server — and the resources bundled next to it — could live in.
///
/// Windows and macOS keep the sidecar beside the executable, the Linux bundles do not:
/// deb/rpm install the binary into `/usr/bin` while the sidecar, the bundled `node` runtime
/// and the PO-token generator go to `/usr/lib/<name>/`, and an AppImage mounts that same
/// tree read-only under `$APPDIR`. Tauri's own `resource_dir()` knows those rules, so it is
/// tried first; the rest cover installs outside `/usr` (`/opt`, an extracted deb), where the
/// same `../lib/<name>` relation still holds, and a raw cargo build in `target/release`.
fn server_search_dirs(app: &tauri::AppHandle) -> Vec<std::path::PathBuf> {
    let mut dirs: Vec<std::path::PathBuf> = Vec::new();

    if let Ok(res) = app.path().resource_dir() {
        dirs.push(res);
    }

    if let Ok(exe) = std::env::current_exe() {
        if let Some(exe_dir) = exe.parent() {
            dirs.push(exe_dir.to_path_buf());
            // /usr/bin/<name> → /usr/lib/<name>, /opt/app/bin/<name> → /opt/app/lib/<name>
            if let (Some(prefix), Some(name)) = (exe_dir.parent(), exe.file_name()) {
                dirs.push(prefix.join("lib").join(name));
            }
        }
        // AppImage: the runtime exports APPDIR pointing at the mounted tree.
        if let Ok(appdir) = std::env::var("APPDIR") {
            let root = std::path::PathBuf::from(appdir);
            if let Some(name) = exe.file_name() {
                dirs.push(root.join("usr/lib").join(name));
            }
            dirs.push(root.join("usr/bin"));
        }
    }

    dirs.retain(|d| d.is_dir());
    dirs
}

#[allow(dead_code)]
pub fn start_server(app: &tauri::AppHandle) {
    let server_bin = if cfg!(windows) { "nest-music-server.exe" } else { "nest-music-server" };

    let dirs = server_search_dirs(app);
    let server_exe = match dirs.iter().map(|d| d.join(server_bin)).find(|p| p.exists()) {
        Some(p) => {
            eprintln!("[server] Found binary at: {}", p.display());
            p
        }
        None => {
            eprintln!("[server] Binary '{}' not found.", server_bin);
            for d in &dirs {
                eprintln!("[server]   - {}", d.join(server_bin).display());
            }
            return;
        }
    };

    let mut cmd = std::process::Command::new(&server_exe);
    // Hand the frozen server the bundle's resource dir: it looks for its `node` runtime
    // (yt-dlp EJS signature/n-sig solving) and the bgutil PO-token generator next to
    // `sys.executable`, which on Linux is /usr/bin — not where those two are installed.
    // KODAMA_NODE / KODAMA_POT_SERVER are overrides server.py already understands, and it
    // ignores them when the path does not exist, so this is safe on every platform.
    let node_name = if cfg!(windows) { "node.exe" } else { "node" };
    let resource_dir = dirs
        .iter()
        .map(std::path::PathBuf::as_path)
        .find(|d| d.join(node_name).exists())
        .or_else(|| server_exe.parent())
        .map(std::path::Path::to_path_buf);
    if let Some(res) = resource_dir {
        eprintln!("[server] resource dir: {}", res.display());
        cmd.env("KODAMA_NODE", res.join(node_name))
            .env("KODAMA_POT_SERVER", res.join("potgen").join("server"));
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }
    match cmd.spawn() {
        Ok(child) => { *app.state::<ServerProcess>().0.lock().unwrap() = Some(child); }
        Err(e) => { eprintln!("[server] Failed to spawn {}: {}", server_exe.display(), e); return; }
    }

    // Wait for the server to accept connections (runs on a background thread,
    // so blocking here does NOT freeze the UI).
    wait_for_server(15000);
}

pub fn stop_server(app_handle: &tauri::AppHandle) {
    let state: tauri::State<ServerProcess> = app_handle.state();
    let mut child_opt = state.0.lock().ok().and_then(|mut g| g.take());
    kill_existing_server(&mut child_opt);
}
