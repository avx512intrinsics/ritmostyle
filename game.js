"use strict";

const laneKeys = ["d", "f", "j", "k"];
const laneLabels = ["D", "F", "J", "K"];
const laneColors = ["#f15a4a", "#f4c542", "#25d0b2", "#4aa8ff"];
const judgementWindows = [
  { name: "Perfect", limit: 0.055, points: 1000 },
  { name: "Great", limit: 0.095, points: 650 },
  { name: "Good", limit: 0.145, points: 300 }
];

const difficulties = {
  chill: {
    label: "Neon clave",
    bpm: 96,
    measures: 12,
    density: 0.56,
    leadTime: 1.75
  },
  metro: {
    label: "Metro sync",
    bpm: 116,
    measures: 14,
    density: 0.7,
    leadTime: 1.55
  },
  rush: {
    label: "Rushline",
    bpm: 138,
    measures: 16,
    density: 0.84,
    leadTime: 1.35
  }
};

const canvas = document.querySelector("#game-canvas");
const context = canvas.getContext("2d");
const scoreElement = document.querySelector("#score");
const comboElement = document.querySelector("#combo");
const bestElement = document.querySelector("#best");
const gradeElement = document.querySelector("#grade");
const judgementElement = document.querySelector("#judgement");
const trackNameElement = document.querySelector("#track-name");
const hitFlashElement = document.querySelector("#hit-flash");
const startButton = document.querySelector("#start-button");
const pauseButton = document.querySelector("#pause-button");
const difficultyButtons = [...document.querySelectorAll(".difficulty-button")];
const padButtons = [...document.querySelectorAll(".pads button")];

const bestScoreKey = "ritmostyle-best-score";

const state = {
  audio: null,
  master: null,
  animationId: 0,
  chart: [],
  difficulty: "chill",
  started: false,
  paused: false,
  ended: false,
  score: 0,
  combo: 0,
  bestCombo: 0,
  hits: 0,
  misses: 0,
  totalNotes: 0,
  startTime: 0,
  pauseStartedAt: 0,
  pausedDuration: 0,
  lastSchedulerBeat: -1,
  countdownUntil: 0,
  lastSongTime: 0,
  pressedLanes: new Set(),
  judgementTimeout: 0
};

let canvasWidth = canvas.width;
let canvasHeight = canvas.height;

function buildChart(settings) {
  const beatDuration = 60 / settings.bpm;
  const notes = [];
  const totalBeats = settings.measures * 4;
  const motif = [
    [0],
    [2],
    [1, 3],
    [2],
    [0, 1],
    [3],
    [1],
    [0, 2]
  ];

  for (let beat = 0; beat < totalBeats; beat += 1) {
    const base = motif[beat % motif.length];
    base.forEach((lane, index) => {
      notes.push({
        id: `b${beat}-${lane}-${index}`,
        lane,
        time: 1 + beat * beatDuration,
        hit: false,
        missed: false
      });
    });

    if (beat > 3 && beat % 2 === 1 && (beat % 8) / 8 < settings.density) {
      notes.push({
        id: `off-${beat}`,
        lane: (beat + 2) % 4,
        time: 1 + (beat + 0.5) * beatDuration,
        hit: false,
        missed: false
      });
    }

    if (settings.density > 0.8 && beat > 7 && beat % 4 === 2) {
      notes.push({
        id: `rush-${beat}`,
        lane: (beat + 1) % 4,
        time: 1 + (beat + 0.75) * beatDuration,
        hit: false,
        missed: false
      });
    }
  }

  return notes.sort((a, b) => a.time - b.time);
}

function ensureAudio() {
  if (state.audio) {
    return state.audio;
  }

  const AudioContext = window.AudioContext || window.webkitAudioContext;
  state.audio = new AudioContext();
  state.master = state.audio.createGain();
  state.master.gain.value = 0.18;
  state.master.connect(state.audio.destination);
  return state.audio;
}

function playTone(frequency, when, duration, type, gainValue) {
  if (!state.audio || !state.master) {
    return;
  }

  const oscillator = state.audio.createOscillator();
  const gain = state.audio.createGain();
  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, when);
  gain.gain.setValueAtTime(0.0001, when);
  gain.gain.exponentialRampToValueAtTime(gainValue, when + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);
  oscillator.connect(gain);
  gain.connect(state.master);
  oscillator.start(when);
  oscillator.stop(when + duration + 0.02);
}

function playNoise(when, duration, gainValue) {
  if (!state.audio || !state.master) {
    return;
  }

  const bufferLength = Math.max(1, Math.floor(state.audio.sampleRate * duration));
  const buffer = state.audio.createBuffer(1, bufferLength, state.audio.sampleRate);
  const data = buffer.getChannelData(0);

  for (let index = 0; index < bufferLength; index += 1) {
    data[index] = Math.random() * 2 - 1;
  }

  const source = state.audio.createBufferSource();
  const filter = state.audio.createBiquadFilter();
  const gain = state.audio.createGain();
  filter.type = "highpass";
  filter.frequency.value = 1600;
  gain.gain.setValueAtTime(gainValue, when);
  gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);
  source.buffer = buffer;
  source.connect(filter);
  filter.connect(gain);
  gain.connect(state.master);
  source.start(when);
}

function scheduleBeat(beatIndex, when) {
  const settings = difficulties[state.difficulty];
  const beatDuration = 60 / settings.bpm;
  const isDownbeat = beatIndex % 4 === 0;
  const isBackbeat = beatIndex % 4 === 2;

  playTone(isDownbeat ? 92 : 128, when, 0.08, "sine", isDownbeat ? 0.44 : 0.22);

  if (isBackbeat) {
    playNoise(when + 0.015, 0.08, 0.18);
  }

  playTone(740, when + beatDuration / 2, 0.03, "triangle", 0.08);
}

function scheduleTrack(songTime) {
  if (!state.audio || state.paused || state.ended) {
    return;
  }

  const settings = difficulties[state.difficulty];
  const beatDuration = 60 / settings.bpm;
  const nextBeat = Math.floor(songTime / beatDuration);
  const scheduleAhead = Math.ceil(1.2 / beatDuration);

  for (let beat = Math.max(0, nextBeat); beat <= nextBeat + scheduleAhead; beat += 1) {
    if (beat > state.lastSchedulerBeat) {
      const beatSongTime = beat * beatDuration;
      const delay = Math.max(0.01, beatSongTime - songTime);
      scheduleBeat(beat, state.audio.currentTime + delay);
      state.lastSchedulerBeat = beat;
    }
  }
}

function currentSongTime() {
  if (!state.audio || !state.started) {
    return 0;
  }

  if (state.paused) {
    return Math.max(0, state.pauseStartedAt - state.startTime - state.pausedDuration);
  }

  return Math.max(0, state.audio.currentTime - state.startTime - state.pausedDuration);
}

function resetRun() {
  const settings = difficulties[state.difficulty];
  state.chart = buildChart(settings);
  state.started = false;
  state.paused = false;
  state.ended = false;
  state.score = 0;
  state.combo = 0;
  state.bestCombo = 0;
  state.hits = 0;
  state.misses = 0;
  state.totalNotes = state.chart.length;
  state.startTime = 0;
  state.pauseStartedAt = 0;
  state.pausedDuration = 0;
  state.lastSchedulerBeat = -1;
  state.countdownUntil = 0;
  state.lastSongTime = 0;
  state.pressedLanes.clear();
  updateStats();
  showJudgement("Ready");
  pauseButton.disabled = true;
  pauseButton.textContent = "Pause";
  startButton.textContent = "Start";
}

async function startGame() {
  const audio = ensureAudio();

  if (audio.state === "suspended") {
    await audio.resume();
  }

  resetRun();
  state.started = true;
  state.startTime = audio.currentTime + 0.55;
  state.countdownUntil = state.startTime;
  pauseButton.disabled = false;
  startButton.textContent = "Restart";
  showJudgement("Get set");
  cancelAnimationFrame(state.animationId);
  state.animationId = requestAnimationFrame(tick);
}

function handleAudioError() {
  showJudgement("Audio blocked");
  pauseButton.disabled = true;
}

async function togglePause() {
  if (!state.started || state.ended || !state.audio) {
    return;
  }

  if (state.paused) {
    if (state.audio.state === "suspended") {
      await state.audio.resume();
    }

    state.paused = false;
    state.pausedDuration += state.audio.currentTime - state.pauseStartedAt;
    pauseButton.textContent = "Pause";
    state.animationId = requestAnimationFrame(tick);
    return;
  }

  state.paused = true;
  state.pauseStartedAt = state.audio.currentTime;
  pauseButton.textContent = "Resume";
  showJudgement("Paused");
  state.audio.suspend().catch(handleAudioError);
}

function finishGame() {
  state.ended = true;
  state.started = false;
  pauseButton.disabled = true;
  startButton.textContent = "Restart";
  const grade = calculateGrade();
  showJudgement(`Grade ${grade}`);
  saveBestScore();
  updateStats();
  drawPlayfield(state.lastSongTime);
}

function calculateGrade() {
  if (!state.totalNotes) {
    return "--";
  }

  const accuracy = state.hits / state.totalNotes;

  if (accuracy >= 0.96) return "S";
  if (accuracy >= 0.88) return "A";
  if (accuracy >= 0.76) return "B";
  if (accuracy >= 0.62) return "C";
  return "D";
}

function saveBestScore() {
  const best = Number(localStorage.getItem(bestScoreKey) || "0");

  if (state.score > best) {
    localStorage.setItem(bestScoreKey, String(state.score));
  }
}

function updateStats() {
  const best = Math.max(Number(localStorage.getItem(bestScoreKey) || "0"), state.score);
  scoreElement.textContent = state.score.toLocaleString();
  comboElement.textContent = String(state.combo);
  bestElement.textContent = best.toLocaleString();
  gradeElement.textContent = state.started || state.ended ? calculateGrade() : "--";
  trackNameElement.textContent = difficulties[state.difficulty].label;
}

function showJudgement(text) {
  clearTimeout(state.judgementTimeout);
  judgementElement.textContent = text;
  judgementElement.classList.add("is-visible");
  state.judgementTimeout = setTimeout(() => {
    if (state.started && !state.paused) {
      judgementElement.textContent = "";
    }
  }, 620);
}

function flashHitLine() {
  hitFlashElement.classList.add("is-visible");
  setTimeout(() => hitFlashElement.classList.remove("is-visible"), 90);
}

function hitLane(lane) {
  pulsePad(lane);

  if (!state.started || state.paused || state.ended) {
    return;
  }

  const songTime = currentSongTime();
  let closest = null;
  let closestDelta = Infinity;

  for (const note of state.chart) {
    if (note.lane !== lane || note.hit || note.missed) {
      continue;
    }

    const delta = Math.abs(note.time - songTime);

    if (delta < closestDelta) {
      closest = note;
      closestDelta = delta;
    }
  }

  const judgement = judgementWindows.find((window) => closestDelta <= window.limit);

  if (!closest || !judgement) {
    state.combo = 0;
    showJudgement("Miss");
    updateStats();
    playTone(84, state.audio.currentTime, 0.07, "sawtooth", 0.12);
    return;
  }

  closest.hit = true;
  state.hits += 1;
  state.combo += 1;
  state.bestCombo = Math.max(state.bestCombo, state.combo);
  state.score += judgement.points + Math.min(400, state.combo * 8);
  showJudgement(judgement.name);
  flashHitLine();
  playTone(260 + lane * 82, state.audio.currentTime, 0.08, "triangle", 0.18);
  updateStats();
}

function markMisses(songTime) {
  for (const note of state.chart) {
    if (note.hit || note.missed) {
      continue;
    }

    if (songTime - note.time > 0.18) {
      note.missed = true;
      state.misses += 1;
      state.combo = 0;
      showJudgement("Miss");
      updateStats();
    }
  }
}

function resizeCanvas() {
  const bounds = canvas.getBoundingClientRect();
  const ratio = Math.max(1, window.devicePixelRatio || 1);
  const width = Math.max(360, Math.floor(bounds.width * ratio));
  const height = Math.max(360, Math.floor(bounds.height * ratio));

  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  canvasWidth = width;
  canvasHeight = height;
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
}

function drawPlayfield(songTime) {
  const width = canvasWidth / Math.max(1, window.devicePixelRatio || 1);
  const height = canvasHeight / Math.max(1, window.devicePixelRatio || 1);
  const laneWidth = width / 4;
  const hitY = height * 0.84;
  const topY = height * 0.05;
  const travelDistance = hitY - topY;
  const settings = difficulties[state.difficulty];

  context.clearRect(0, 0, width, height);
  drawBackground(width, height, laneWidth, hitY);

  for (const note of state.chart) {
    if (note.hit || note.missed) {
      continue;
    }

    const secondsUntilHit = note.time - songTime;

    if (secondsUntilHit > settings.leadTime || secondsUntilHit < -0.24) {
      continue;
    }

    const progress = 1 - secondsUntilHit / settings.leadTime;
    const y = topY + progress * travelDistance;
    const x = note.lane * laneWidth + laneWidth / 2;
    drawNote(x, y, laneWidth, laneColors[note.lane], secondsUntilHit);
  }

  drawHitLine(width, hitY, laneWidth);

  if (!state.started && !state.ended) {
    drawCenterText(width, height, "RITMOSTYLE", "D  F  J  K");
  } else if (state.countdownUntil > 0 && state.audio) {
    const remaining = Math.max(0, state.countdownUntil - state.audio.currentTime);

    if (remaining > 0) {
      drawCenterText(width, height, remaining > 0.28 ? "READY" : "GO", "");
    }
  } else if (state.paused) {
    drawCenterText(width, height, "PAUSED", "");
  } else if (state.ended) {
    drawCenterText(width, height, `GRADE ${calculateGrade()}`, `${state.score.toLocaleString()} POINTS`);
  }
}

function drawBackground(width, height, laneWidth, hitY) {
  const gradient = context.createLinearGradient(0, 0, 0, height);
  gradient.addColorStop(0, "#171b23");
  gradient.addColorStop(1, "#101217");
  context.fillStyle = gradient;
  context.fillRect(0, 0, width, height);

  for (let lane = 0; lane < 4; lane += 1) {
    context.fillStyle = lane % 2 === 0 ? "rgba(255,255,255,0.025)" : "rgba(255,255,255,0.045)";
    context.fillRect(lane * laneWidth, 0, laneWidth, height);

    context.strokeStyle = laneColors[lane];
    context.globalAlpha = 0.32;
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(lane * laneWidth + laneWidth / 2, 0);
    context.lineTo(lane * laneWidth + laneWidth / 2, height);
    context.stroke();
    context.globalAlpha = 1;

    context.fillStyle = "rgba(247,241,232,0.54)";
    context.font = "800 18px Inter, sans-serif";
    context.textAlign = "center";
    context.fillText(laneLabels[lane], lane * laneWidth + laneWidth / 2, hitY + 48);
  }
}

function drawHitLine(width, hitY, laneWidth) {
  context.save();
  context.shadowColor = "rgba(247,241,232,0.65)";
  context.shadowBlur = 18;
  context.fillStyle = "rgba(247,241,232,0.92)";
  context.fillRect(22, hitY - 4, width - 44, 8);
  context.restore();

  for (let lane = 0; lane < 4; lane += 1) {
    const x = lane * laneWidth + laneWidth / 2;
    context.strokeStyle = laneColors[lane];
    context.lineWidth = state.pressedLanes.has(lane) ? 6 : 3;
    context.beginPath();
    context.arc(x, hitY, Math.max(24, laneWidth * 0.18), 0, Math.PI * 2);
    context.stroke();
  }
}

function drawNote(x, y, laneWidth, color, secondsUntilHit) {
  const radius = Math.max(18, laneWidth * 0.16);
  const squish = Math.max(0.78, 1 - Math.abs(secondsUntilHit) * 1.4);

  context.save();
  context.translate(x, y);
  context.scale(1, squish);
  context.shadowColor = color;
  context.shadowBlur = 18;
  context.fillStyle = color;
  fillRoundRect(context, -radius, -radius, radius * 2, radius * 2, 8);
  context.fillStyle = "rgba(16,18,23,0.84)";
  context.fillRect(-radius + 7, -3, radius * 2 - 14, 6);
  context.restore();
}

function fillRoundRect(drawingContext, x, y, width, height, radius) {
  drawingContext.beginPath();

  if (typeof drawingContext.roundRect === "function") {
    drawingContext.roundRect(x, y, width, height, radius);
  } else {
    drawingContext.moveTo(x + radius, y);
    drawingContext.lineTo(x + width - radius, y);
    drawingContext.quadraticCurveTo(x + width, y, x + width, y + radius);
    drawingContext.lineTo(x + width, y + height - radius);
    drawingContext.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    drawingContext.lineTo(x + radius, y + height);
    drawingContext.quadraticCurveTo(x, y + height, x, y + height - radius);
    drawingContext.lineTo(x, y + radius);
    drawingContext.quadraticCurveTo(x, y, x + radius, y);
  }

  drawingContext.fill();
}

function drawCenterText(width, height, headline, subline) {
  context.save();
  context.fillStyle = "rgba(16,18,23,0.72)";
  context.fillRect(0, height * 0.34, width, height * 0.2);
  context.fillStyle = "#f7f1e8";
  context.font = "900 38px Inter, sans-serif";
  context.textAlign = "center";
  context.fillText(headline, width / 2, height * 0.43);

  if (subline) {
    context.fillStyle = "rgba(247,241,232,0.72)";
    context.font = "800 16px Inter, sans-serif";
    context.fillText(subline, width / 2, height * 0.49);
  }

  context.restore();
}

function tick() {
  resizeCanvas();

  if (state.paused) {
    drawPlayfield(currentSongTime());
    return;
  }

  const songTime = currentSongTime();
  state.lastSongTime = songTime;
  scheduleTrack(songTime);
  markMisses(songTime);
  drawPlayfield(songTime);

  const lastNoteTime = state.chart.length ? state.chart[state.chart.length - 1].time : 0;

  if (state.started && songTime > lastNoteTime + 1.5) {
    finishGame();
  }

  if (!state.ended) {
    state.animationId = requestAnimationFrame(tick);
  }
}

function pulsePad(lane) {
  const pad = padButtons[lane];

  if (!pad) {
    return;
  }

  pad.classList.add("is-pressed");
  window.setTimeout(() => pad.classList.remove("is-pressed"), 110);
}

function setDifficulty(nextDifficulty) {
  if (!difficulties[nextDifficulty]) {
    return;
  }

  state.difficulty = nextDifficulty;
  difficultyButtons.forEach((button) => {
    button.classList.toggle("is-active", button.dataset.difficulty === nextDifficulty);
  });
  resetRun();
  drawPlayfield(0);
}

function bindEvents() {
  startButton.addEventListener("click", () => {
    startGame().catch(handleAudioError);
  });

  pauseButton.addEventListener("click", () => {
    togglePause().catch(handleAudioError);
  });

  difficultyButtons.forEach((button) => {
    button.addEventListener("click", () => setDifficulty(button.dataset.difficulty));
  });

  padButtons.forEach((button) => {
    const lane = Number(button.dataset.lane);
    button.addEventListener("pointerdown", (event) => {
      event.preventDefault();
      hitLane(lane);
    });
  });

  window.addEventListener("keydown", (event) => {
    const key = event.key.toLowerCase();

    if (event.repeat) {
      return;
    }

    if (key === "enter") {
      event.preventDefault();
      startGame().catch(handleAudioError);
      return;
    }

    if (key === " ") {
      event.preventDefault();
      togglePause().catch(handleAudioError);
      return;
    }

    const lane = laneKeys.indexOf(key);

    if (lane >= 0) {
      event.preventDefault();
      state.pressedLanes.add(lane);
      hitLane(lane);
    }
  });

  window.addEventListener("keyup", (event) => {
    const lane = laneKeys.indexOf(event.key.toLowerCase());

    if (lane >= 0) {
      state.pressedLanes.delete(lane);
    }
  });

  window.addEventListener("resize", () => {
    resizeCanvas();
    drawPlayfield(state.lastSongTime);
  });
}

bindEvents();
resetRun();
resizeCanvas();
drawPlayfield(0);
