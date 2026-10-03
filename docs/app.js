// Nest Music site — interactions
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // nav scroll state
  const nav = $("#nav");
  const onScroll = () => nav.classList.toggle("scrolled", scrollY > 24);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // scroll reveals
  const io = new IntersectionObserver((es) => es.forEach((e) => {
    if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
  }), { threshold: 0.15 });
  $$(".reveal").forEach((el) => io.observe(el));

  // hero phone tilt
  const tilt = $("#tilt"), phone = $(".phone");
  if (tilt && matchMedia("(pointer:fine)").matches) {
    tilt.addEventListener("mousemove", (e) => {
      const r = tilt.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width - 0.5;
      const py = (e.clientY - r.top) / r.height - 0.5;
      phone.style.setProperty("--ry", `${(-8 + px * 14).toFixed(2)}deg`);
      phone.style.setProperty("--rx", `${(3 - py * 10).toFixed(2)}deg`);
    });
    tilt.addEventListener("mouseleave", () => {
      phone.style.setProperty("--ry", "-8deg");
      phone.style.setProperty("--rx", "3deg");
    });
  }

  // cursor-follow glow on cards
  $$(".glowable").forEach((c) => c.addEventListener("pointermove", (e) => {
    const r = c.getBoundingClientRect();
    c.style.setProperty("--mx", `${e.clientX - r.left}px`);
    c.style.setProperty("--my", `${e.clientY - r.top}px`);
  }));

  // carousel arrows
  const car = $("#carousel");
  const step = () => Math.min(car.clientWidth * 0.8, 560);
  $("#prev").onclick = () => car.scrollBy({ left: -step(), behavior: "smooth" });
  $("#next").onclick = () => car.scrollBy({ left: step(), behavior: "smooth" });

  // AI demo loop (typewriter -> generate -> progress -> rows -> reset)
  const typed = $("#typed"), gen = $("#gen"), bar = $("#bar"),
    count = $("#count"), rows = $$(".ai-row");
  const PROMPT = "high-energy workout";
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  async function cycle() {
    for (;;) {
      typed.textContent = ""; bar.style.width = "0"; count.textContent = "0";
      rows.forEach((r) => r.classList.remove("in"));
      await wait(900);
      for (let i = 1; i <= PROMPT.length; i++) { typed.textContent = PROMPT.slice(0, i); await wait(55); }
      await wait(500);
      gen.classList.add("press"); await wait(220); gen.classList.remove("press");
      const t0 = performance.now(), D = 1600;
      await new Promise((res) => {
        const tick = (n) => {
          const p = Math.min(1, (n - t0) / D);
          bar.style.width = `${(100 * (1 - Math.pow(1 - p, 3))).toFixed(1)}%`;
          count.textContent = Math.round(25 * p);
          if (p < 1) requestAnimationFrame(tick); else res();
        };
        requestAnimationFrame(tick);
      });
      for (const r of rows) { r.classList.add("in"); await wait(180); }
      await wait(2600);
    }
  }
  cycle();

  // pause hero video off-screen
  const vid = $(".phone-screen video");
  if (vid) new IntersectionObserver((es) => es.forEach((e) => {
    e.isIntersecting ? vid.play().catch(() => {}) : vid.pause();
  }), { threshold: 0.1 }).observe(vid);

  $("#yy").textContent = new Date().getFullYear();
})();
