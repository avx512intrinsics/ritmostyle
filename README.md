# RitmoStyle

RitmoStyle is a small browser rhythm game built with plain HTML, CSS, Canvas, and Web Audio. Notes fall through four lanes; hit them on the beat to build combo and score.

## Run

```bash
npm start
```

Then open the local URL printed by the server.

The game can also be opened directly from `index.html` in a browser, though the local server is the most reliable option.

## Controls

- `D`, `F`, `J`, `K`: hit the four lanes
- `Space`: pause or resume
- `Enter`: start or restart

Touch and mouse controls are available through the four pads below the playfield.

## Validate

```bash
npm test
```

The validation script checks JavaScript syntax and verifies that the static game files contain the expected app surface.
