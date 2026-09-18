# Mini Chess

A small chess practice game with:

- HTML/CSS/JavaScript frontend
- Java backend using Java's built-in HTTP server
- Real chess movement rules
- Check, checkmate and stalemate
- Castling
- En passant
- Pawn promotion to queen
- Simple minimax bot
- Bot levels from 100 to 900 ELO (approximate difficulty, not official ratings)

## Requirements

- Java JDK 17 or newer
- Any modern browser
- VS Code is recommended but not required

## Run the Java backend

Open a terminal in `backend`:

```bash
javac ChessServer.java
java ChessServer
```

You should see:

```text
Mini Chess backend running on http://localhost:8080
```

## Run the frontend

The easiest way is VS Code + Live Server.

1. Open the `frontend` folder in VS Code.
2. Install the **Live Server** extension.
3. Right-click `index.html`.
4. Select **Open with Live Server**.
5. The frontend will open in your browser.

The frontend expects the Java backend at:

```text
http://localhost:8080
```

## Project structure

```text
mini-chess/
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
└── backend/
    └── ChessServer.java
```

## Bot levels

These are intentionally approximate practice levels:

100 / 250 / 400 / 500 / 600 / 750 / 900 ELO

Higher levels search more positions with minimax. The lower levels also intentionally make random/weak choices.

## Next upgrades

- Clocks
- Undo
- Move highlighting
- Better chess notation
- Threefold repetition
- 50-move rule
- Insufficient-material detection
- Stronger evaluation
- Stockfish integration
- Save games
