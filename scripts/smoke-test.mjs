import { readFile } from "node:fs/promises";

const checks = [
  ["index.html", ["RitmoStyle", "game-canvas", "data-lane=\"0\"", "assets/ritmostyle-icon.svg"]],
  ["styles.css", [".stage", ".pads", "--teal", "@media"]],
  ["game.js", ["buildChart", "AudioContext", "hitLane", "requestAnimationFrame"]],
  ["README.md", ["npm start", "npm test", "D`, `F`, `J`, `K"]]
];

const failures = [];

for (const [file, expectedSnippets] of checks) {
  const content = await readFile(new URL(`../${file}`, import.meta.url), "utf8");

  for (const snippet of expectedSnippets) {
    if (!content.includes(snippet)) {
      failures.push(`${file} is missing ${snippet}`);
    }
  }
}

if (failures.length) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("RitmoStyle static checks passed.");
