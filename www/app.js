(() => {
  "use strict";

  const LAYOUT = [
    ["й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"],
    ["ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"],
    ["я", "ч", "с", "м", "и", "т", "ь", "б", "ю"],
    ["⌫", "␣", "↵"],
  ];

  // Relative letter frequency for Russian (approx). Higher = preferred when ambiguous.
  const FREQ = {
    о: 1.0, е: 0.92, а: 0.9, и: 0.82, н: 0.78, т: 0.72, с: 0.68, р: 0.62,
    в: 0.58, л: 0.55, к: 0.5, м: 0.46, д: 0.44, п: 0.42, у: 0.4, я: 0.36,
    ы: 0.34, ь: 0.32, г: 0.3, з: 0.28, б: 0.26, ч: 0.24, й: 0.2, х: 0.18,
    ж: 0.16, ш: 0.15, ю: 0.14, ц: 0.12, щ: 0.1, э: 0.08, ф: 0.06, ъ: 0.04,
  };

  const STORE_KEY = "applklava_v1";

  const state = {
    mode: "smart", // smart | normal
    miss: { smart: 0, normal: 0 },
    bias: { x: 0, y: 0 },
    lastCharAt: 0,
    lastPointer: null,
    keys: [],
    settings: {
      zoneWidth: 1.35,
      freqWeight: 0.55,
      speedForgive: 0.7,
      calibRate: 0.12,
      showRing: true,
    },
    debug: false,
  };

  const el = {
    output: document.getElementById("output"),
    keyboard: document.getElementById("keyboard"),
    zoneRing: document.getElementById("zoneRing"),
    chipSmart: document.getElementById("chipSmart"),
    chipNormal: document.getElementById("chipNormal"),
    missSmart: document.getElementById("missSmart"),
    missNormal: document.getElementById("missNormal"),
    btnResetStats: document.getElementById("btnResetStats"),
    btnInfo: document.getElementById("btnInfo"),
    debugPanel: document.getElementById("debugPanel"),
    dbgHit: document.getElementById("dbgHit"),
    dbgChose: document.getElementById("dbgChose"),
    dbgBias: document.getElementById("dbgBias"),
    dbgSpeed: document.getElementById("dbgSpeed"),
    dbgZone: document.getElementById("dbgZone"),
    slZoneWidth: document.getElementById("slZoneWidth"),
    slFreq: document.getElementById("slFreq"),
    slSpeed: document.getElementById("slSpeed"),
    slCalib: document.getElementById("slCalib"),
    chkRing: document.getElementById("chkRing"),
    valZoneWidth: document.getElementById("valZoneWidth"),
    valFreq: document.getElementById("valFreq"),
    valSpeed: document.getElementById("valSpeed"),
    valCalib: document.getElementById("valCalib"),
    btnToggleSettings: document.getElementById("btnToggleSettings"),
    settingsBody: document.getElementById("settingsBody"),
  };

  function load() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) return;
      const data = JSON.parse(raw);
      if (data.mode) state.mode = data.mode;
      if (data.miss) state.miss = { ...state.miss, ...data.miss };
      if (data.bias) state.bias = { ...state.bias, ...data.bias };
      if (data.settings) state.settings = { ...state.settings, ...data.settings };
    } catch (_) { /* ignore */ }
  }

  function save() {
    localStorage.setItem(
      STORE_KEY,
      JSON.stringify({
        mode: state.mode,
        miss: state.miss,
        bias: state.bias,
        settings: state.settings,
      })
    );
  }

  function buildKeyboard() {
    el.keyboard.innerHTML = "";
    state.keys = [];

    LAYOUT.forEach((rowKeys, rowIndex) => {
      const row = document.createElement("div");
      row.className = "row";

      rowKeys.forEach((label) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "key";
        btn.textContent = label === "␣" ? "пробел" : label;
        btn.dataset.key = label;

        if (label === "␣") btn.classList.add("space");
        if (label === "⌫" || label === "↵") btn.classList.add("wide", "special");

        const meta = {
          label,
          el: btn,
          center: { x: 0, y: 0 },
          halfW: 0,
          halfH: 0,
          row: rowIndex,
        };
        state.keys.push(meta);
        row.appendChild(btn);
      });

      el.keyboard.appendChild(row);
    });

    // Use pointerdown on container for raw coords (not button hit-testing alone).
    el.keyboard.addEventListener("pointerdown", onPointerDown, { passive: false });
  }

  function measureKeys() {
    state.keys.forEach((k) => {
      const r = k.el.getBoundingClientRect();
      k.center = { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      k.halfW = r.width / 2;
      k.halfH = r.height / 2;
    });
  }

  function setMode(mode) {
    state.mode = mode === "normal" ? "normal" : "smart";
    el.chipSmart.classList.toggle("active", state.mode === "smart");
    el.chipNormal.classList.toggle("active", state.mode === "normal");
    el.chipSmart.setAttribute("aria-selected", state.mode === "smart" ? "true" : "false");
    el.chipNormal.setAttribute("aria-selected", state.mode === "normal" ? "true" : "false");
    document.body.dataset.mode = state.mode;
    save();
  }

  function updateStatsUI() {
    el.missSmart.textContent = String(state.miss.smart);
    el.missNormal.textContent = String(state.miss.normal);
  }

  function syncSliders() {
    const s = state.settings;
    el.slZoneWidth.value = s.zoneWidth;
    el.slFreq.value = s.freqWeight;
    el.slSpeed.value = s.speedForgive;
    el.slCalib.value = s.calibRate;
    el.chkRing.checked = s.showRing;
    el.valZoneWidth.textContent = `${Number(s.zoneWidth).toFixed(2)}×`;
    el.valFreq.textContent = Number(s.freqWeight).toFixed(2);
    el.valSpeed.textContent = Number(s.speedForgive).toFixed(2);
    el.valCalib.textContent = Number(s.calibRate).toFixed(2);
  }

  function dist2(a, b) {
    const dx = a.x - b.x;
    const dy = a.y - b.y;
    return dx * dx + dy * dy;
  }

  function hardHit(point) {
    // Strict geometric hit-box (normal mode).
    for (const k of state.keys) {
      if (
        Math.abs(point.x - k.center.x) <= k.halfW &&
        Math.abs(point.y - k.center.y) <= k.halfH
      ) {
        return k;
      }
    }
    // Nearest fallback if finger landed in a gap.
    let best = null;
    let bestD = Infinity;
    for (const k of state.keys) {
      const d = dist2(point, k.center);
      if (d < bestD) {
        bestD = d;
        best = k;
      }
    }
    return best;
  }

  function smartPick(point, speedPxPerMs) {
    const s = state.settings;
    const calibrated = {
      x: point.x - state.bias.x,
      y: point.y - state.bias.y,
    };

    // Faster taps → larger effective zone (more forgiveness).
    const speedBoost = 1 + Math.min(2.5, speedPxPerMs * 18) * s.speedForgive;
    const zoneMul = s.zoneWidth * speedBoost;

    let best = null;
    let bestScore = -Infinity;

    for (const k of state.keys) {
      // Skip special keys from soft scoring — they keep hard hit.
      if (k.label === "⌫" || k.label === "↵" || k.label === "␣") continue;

      const dx = (calibrated.x - k.center.x) / (k.halfW * zoneMul);
      const dy = (calibrated.y - k.center.y) / (k.halfH * zoneMul);
      const nd2 = dx * dx + dy * dy;
      if (nd2 > 2.8) continue; // too far even for soft zone

      const freq = FREQ[k.label] ?? 0.05;
      // score = frequency^w / distance²  (with floor to avoid div0)
      const score = Math.pow(freq, s.freqWeight) / (nd2 + 0.08);
      if (score > bestScore) {
        bestScore = score;
        best = k;
      }
    }

    // If finger is on space/backspace/enter hard zone, prefer that.
    const hard = hardHit(point);
    if (hard && (hard.label === "⌫" || hard.label === "↵" || hard.label === "␣")) {
      return { chose: hard, hit: hard, zoneMul, calibrated };
    }

    if (!best) best = hard;
    return { chose: best, hit: hard, zoneMul, calibrated };
  }

  function showRing(center, radius) {
    if (!state.settings.showRing || state.mode !== "smart") {
      el.zoneRing.hidden = true;
      el.zoneRing.classList.remove("show");
      return;
    }
    const wrap = el.zoneRing.parentElement.getBoundingClientRect();
    el.zoneRing.hidden = false;
    el.zoneRing.style.left = `${center.x - wrap.left}px`;
    el.zoneRing.style.top = `${center.y - wrap.top}px`;
    el.zoneRing.style.width = `${radius * 2}px`;
    el.zoneRing.style.height = `${radius * 2}px`;
    el.zoneRing.classList.add("show");
    clearTimeout(showRing._t);
    showRing._t = setTimeout(() => el.zoneRing.classList.remove("show"), 280);
  }

  function flashKeys(hit, chose) {
    state.keys.forEach((k) => k.el.classList.remove("ghost", "chosen", "active"));
    if (hit) hit.el.classList.add("ghost", "active");
    if (chose) chose.el.classList.add("chosen", "active");
    clearTimeout(flashKeys._t);
    flashKeys._t = setTimeout(() => {
      state.keys.forEach((k) => k.el.classList.remove("ghost", "chosen", "active"));
    }, 180);
  }

  function updateDebug(hit, chose, speed, zoneMul) {
    el.dbgHit.textContent = hit ? hit.label : "—";
    el.dbgChose.textContent = chose ? chose.label : "—";
    el.dbgBias.textContent = `${state.bias.x.toFixed(1)}, ${state.bias.y.toFixed(1)}`;
    el.dbgSpeed.textContent = speed.toFixed(2);
    el.dbgZone.textContent = `${zoneMul.toFixed(2)}×`;
  }

  function insertText(label) {
    const ta = el.output;
    const start = ta.selectionStart ?? ta.value.length;
    const end = ta.selectionEnd ?? ta.value.length;
    let insert = "";
    if (label === "␣") insert = " ";
    else if (label === "↵") insert = "\n";
    else if (label === "⌫") {
      if (start === end && start > 0) {
        ta.value = ta.value.slice(0, start - 1) + ta.value.slice(end);
        ta.selectionStart = ta.selectionEnd = start - 1;
      } else {
        ta.value = ta.value.slice(0, start) + ta.value.slice(end);
        ta.selectionStart = ta.selectionEnd = start;
      }
      return "backspace";
    } else {
      insert = label;
    }
    ta.value = ta.value.slice(0, start) + insert + ta.value.slice(end);
    ta.selectionStart = ta.selectionEnd = start + insert.length;
    return "char";
  }

  function calibrateToward(chose, rawPoint) {
    if (!chose || chose.label === "⌫" || chose.label === "↵" || chose.label === "␣") return;
    const rate = state.settings.calibRate;
    if (rate <= 0) return;
    // Bias = average (finger - keyCenter). Positive bias.x means finger lands right of center.
    const errX = rawPoint.x - chose.center.x;
    const errY = rawPoint.y - chose.center.y;
    state.bias.x = state.bias.x * (1 - rate) + errX * rate;
    state.bias.y = state.bias.y * (1 - rate) + errY * rate;
    save();
  }

  function onPointerDown(e) {
    e.preventDefault();
    measureKeys();

    const point = { x: e.clientX, y: e.clientY };
    const now = performance.now();
    let speed = 0;
    if (state.lastPointer) {
      const dt = Math.max(1, now - state.lastPointer.t);
      speed = Math.hypot(point.x - state.lastPointer.x, point.y - state.lastPointer.y) / dt;
    }
    state.lastPointer = { x: point.x, y: point.y, t: now };

    let hit;
    let chose;
    let zoneMul = 1;

    if (state.mode === "normal") {
      hit = hardHit(point);
      chose = hit;
    } else {
      const res = smartPick(point, speed);
      hit = res.hit;
      chose = res.chose;
      zoneMul = res.zoneMul;
    }

    if (!chose) return;

    flashKeys(hit, chose);
    if (chose.label !== "⌫" && chose.label !== "↵" && chose.label !== "␣") {
      const r = Math.max(chose.halfW, chose.halfH) * zoneMul;
      showRing(chose.center, r);
    }

    const action = insertText(chose.label);

    if (action === "backspace") {
      // Backspace immediately after a letter = miss signal for current mode.
      if (now - state.lastCharAt < 900) {
        state.miss[state.mode] += 1;
        updateStatsUI();
        save();
      }
      state.lastCharAt = 0;
    } else if (action === "char" && chose.label !== "␣" && chose.label !== "↵") {
      state.lastCharAt = now;
      if (state.mode === "smart") calibrateToward(chose, point);
    }

    updateDebug(hit, chose, speed, zoneMul);

    // Keep focus for caret, but avoid mobile OS keyboard.
    el.output.focus({ preventScroll: true });
  }

  // Prevent OS keyboard when focusing textarea.
  el.output.addEventListener("focus", () => {
    el.output.setAttribute("readonly", "readonly");
    setTimeout(() => el.output.removeAttribute("readonly"), 10);
  });

  // Mode chips — single delegated handler (more reliable on touch / automation).
  document.querySelector(".modes").addEventListener(
    "pointerdown",
    (e) => {
      const btn = e.target.closest("[data-mode]");
      if (!btn) return;
      e.preventDefault();
      e.stopPropagation();
      setMode(btn.dataset.mode);
    },
    { passive: false }
  );

  el.btnResetStats.addEventListener("click", () => {
    state.miss.smart = 0;
    state.miss.normal = 0;
    state.bias = { x: 0, y: 0 };
    updateStatsUI();
    save();
    updateDebug(null, null, 0, 1);
  });

  el.btnInfo.addEventListener("click", () => {
    state.debug = !state.debug;
    el.debugPanel.hidden = !state.debug;
    el.btnInfo.style.background = state.debug ? "rgba(61,214,198,0.25)" : "";
  });

  el.btnToggleSettings.addEventListener("click", () => {
    const collapsed = el.settingsBody.classList.toggle("collapsed");
    el.btnToggleSettings.textContent = collapsed ? "развернуть" : "свернуть";
  });

  function bindSlider(input, key, format) {
    input.addEventListener("input", () => {
      state.settings[key] = Number(input.value);
      format();
      save();
    });
  }

  bindSlider(el.slZoneWidth, "zoneWidth", () => {
    el.valZoneWidth.textContent = `${Number(el.slZoneWidth.value).toFixed(2)}×`;
  });
  bindSlider(el.slFreq, "freqWeight", () => {
    el.valFreq.textContent = Number(el.slFreq.value).toFixed(2);
  });
  bindSlider(el.slSpeed, "speedForgive", () => {
    el.valSpeed.textContent = Number(el.slSpeed.value).toFixed(2);
  });
  bindSlider(el.slCalib, "calibRate", () => {
    el.valCalib.textContent = Number(el.slCalib.value).toFixed(2);
  });

  el.chkRing.addEventListener("change", () => {
    state.settings.showRing = el.chkRing.checked;
    save();
  });

  window.addEventListener("resize", () => measureKeys());
  window.addEventListener("orientationchange", () => setTimeout(measureKeys, 200));

  load();
  buildKeyboard();
  setMode(state.mode);
  updateStatsUI();
  syncSliders();
  requestAnimationFrame(measureKeys);

  // Public API for inline handlers / debugging
  window.ApplKlava = {
    setMode,
    getMode: () => state.mode,
    getState: () => ({ ...state, settings: { ...state.settings }, miss: { ...state.miss } }),
  };
})();
