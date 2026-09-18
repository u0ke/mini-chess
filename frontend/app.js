const API = "http://localhost:8080";
const boardEl = document.getElementById("board");
const statusEl = document.getElementById("status");
const historyEl = document.getElementById("history");
const turnEl = document.getElementById("turn");
const moveCountEl = document.getElementById("moveCount");
const opponentEl = document.getElementById("opponent");
const eloEl = document.getElementById("elo");

let state = null;
let selected = null;

const pieces = {
  wK: "♔", wQ: "♕", wR: "♖", wB: "♗", wN: "♘", wP: "♙",
  bK: "♚", bQ: "♛", bR: "♜", bB: "♝", bN: "♞", bP: "♟"
};

async function request(path, options = {}) {
  const response = await fetch(API + path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function loadGame() {
  try {
    state = await request("/state");
    render();
  } catch (e) {
    statusEl.textContent = "Start the Java backend first.";
  }
}

function render() {
  boardEl.innerHTML = "";

  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const sq = document.createElement("button");
      sq.className = `square ${(row + col) % 2 === 0 ? "light" : "dark"}`;

      const piece = state.board[row][col];
      if (piece) {
        const span = document.createElement("span");
        span.className = "piece";
        span.textContent = pieces[piece];
        sq.appendChild(span);
      }

      const coord = `${row},${col}`;
      if (selected === coord) sq.classList.add("selected");

      if (selected && state.legalMoves.some(m => m.toRow === row && m.toCol === col)) {
        sq.classList.add(piece ? "capture" : "legal");
      }

      sq.addEventListener("click", () => clickSquare(row, col));
      boardEl.appendChild(sq);
    }
  }

  opponentEl.textContent = `${state.elo} ELO`;
  turnEl.textContent = state.turn === "w" ? "White" : "Black";
  moveCountEl.textContent = state.moves.length;
  historyEl.innerHTML = state.moves.map((m, i) => `<li>${i + 1}. ${escapeHtml(m)}</li>`).join("");

  if (state.gameOver) {
    statusEl.textContent = state.result;
  } else if (state.turn === "w") {
    statusEl.textContent = "Your turn — click a piece, then its destination.";
  } else {
    statusEl.textContent = "Bot is thinking...";
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#039;"
  }[c]));
}

async function clickSquare(row, col) {
  if (!state || state.gameOver || state.turn !== "w") return;

  const coord = `${row},${col}`;

  if (!selected) {
    if (state.board[row][col]?.startsWith("w")) {
      selected = coord;
      state.legalMoves = await request(`/legal?row=${row}&col=${col}`);
      render();
    }
    return;
  }

  if (selected === coord) {
    selected = null;
    state.legalMoves = [];
    render();
    return;
  }

  const [fromRow, fromCol] = selected.split(",").map(Number);
  const legal = state.legalMoves.find(m => m.toRow === row && m.toCol === col);

  if (!legal) {
    if (state.board[row][col]?.startsWith("w")) {
      selected = coord;
      state.legalMoves = await request(`/legal?row=${row}&col=${col}`);
      render();
    }
    return;
  }

  selected = null;
  state.legalMoves = [];

  try {
    state = await request("/move", {
      method: "POST",
      body: JSON.stringify({
        fromRow, fromCol, toRow: row, toCol: col
      })
    });
    render();
  } catch (e) {
    statusEl.textContent = "Invalid move.";
    await loadGame();
  }
}

async function startGame() {
  selected = null;
  const elo = Number(eloEl.value);
  state = await request("/new", {
    method: "POST",
    body: JSON.stringify({ elo })
  });
  render();
}

document.getElementById("newGame").addEventListener("click", startGame);
document.getElementById("startGame").addEventListener("click", startGame);
loadGame();
