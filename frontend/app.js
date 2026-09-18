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
let selectedMoves = [];

const pieces = {
    wK: "♔",
    wQ: "♕",
    wR: "♖",
    wB: "♗",
    wN: "♘",
    wP: "♙",

    bK: "♚",
    bQ: "♛",
    bR: "♜",
    bB: "♝",
    bN: "♞",
    bP: "♟"
};

async function request(path, options = {}) {
    const response = await fetch(API + path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || "Request failed");
    }

    return response.json();
}

async function loadGame() {
    try {
        state = await request("/state");

        selected = null;
        selectedMoves = [];

        render();
    } catch (error) {
        console.error(error);

        statusEl.textContent =
            "Java backend is not running. Start ChessServer.java first.";
    }
}

function render() {
    if (!state) return;

    boardEl.innerHTML = "";

    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {

            const square = document.createElement("button");

            square.type = "button";

            square.className =
                `square ${(row + col) % 2 === 0 ? "light" : "dark"}`;

            const piece = state.board[row][col];

            /*
             * Selected square
             */
            if (
                selected &&
                selected.row === row &&
                selected.col === col
            ) {
                square.classList.add("selected");
            }

            /*
             * Legal destination
             */
            const legalMove = selectedMoves.find(
                move =>
                    Number(move.toRow) === row &&
                    Number(move.toCol) === col
            );

            if (legalMove) {

                if (piece) {
                    square.classList.add("capture");
                } else {
                    square.classList.add("legal");
                }
            }

            /*
             * Piece
             */
            if (piece) {
                const span = document.createElement("span");

                span.className = "piece";

                span.textContent = pieces[piece] || "?";

                square.appendChild(span);
            }

            square.addEventListener("click", () => {
                handleSquareClick(row, col);
            });

            boardEl.appendChild(square);
        }
    }

    opponentEl.textContent = `${state.elo} ELO`;

    turnEl.textContent =
        state.turn === "w"
            ? "White"
            : "Black";

    moveCountEl.textContent = state.moves.length;

    historyEl.innerHTML = "";

    state.moves.forEach((move, index) => {

        const li = document.createElement("li");

        li.textContent = `${index + 1}. ${move}`;

        historyEl.appendChild(li);
    });

    if (state.gameOver) {

        statusEl.textContent =
            state.result || "Game over";

    } else if (state.turn === "w") {

        if (selected) {
            statusEl.textContent =
                `Selected ${pieceName(state.board[selected.row][selected.col])}. Choose a destination.`;
        } else {
            statusEl.textContent =
                "Your turn — select a piece.";
        }

    } else {

        statusEl.textContent =
            "Bot is thinking...";
    }
}

function pieceName(piece) {

    const names = {
        wK: "White King",
        wQ: "White Queen",
        wR: "White Rook",
        wB: "White Bishop",
        wN: "White Knight",
        wP: "White Pawn",

        bK: "Black King",
        bQ: "Black Queen",
        bR: "Black Rook",
        bN: "Black Knight",
        bP: "Black Pawn"
    };

    return names[piece] || "piece";
}

async function handleSquareClick(row, col) {

    if (!state) return;

    if (state.gameOver) return;

    if (state.turn !== "w") return;

    const clickedPiece = state.board[row][col];

    /*
     * Nothing selected yet
     */
    if (!selected) {

        if (!clickedPiece) {
            return;
        }

        /*
         * Only white pieces can be selected
         */
        if (!clickedPiece.startsWith("w")) {
            statusEl.textContent =
                "It's your turn. Select a white piece.";

            return;
        }

        await selectPiece(row, col);

        return;
    }

    /*
     * Clicking the same piece deselects it
     */
    if (
        selected.row === row &&
        selected.col === col
    ) {

        selected = null;
        selectedMoves = [];

        render();

        return;
    }

    /*
     * Clicking another white piece switches selection
     */
    if (
        clickedPiece &&
        clickedPiece.startsWith("w")
    ) {

        await selectPiece(row, col);

        return;
    }

    /*
     * Check if destination is legal
     */
    const legalMove = selectedMoves.find(
        move =>
            Number(move.toRow) === row &&
            Number(move.toCol) === col
    );

    if (!legalMove) {

        statusEl.textContent =
            "That piece cannot move there.";

        return;
    }

    /*
     * Save starting position
     */
    const fromRow = selected.row;
    const fromCol = selected.col;

    /*
     * Clear selection immediately
     */
    selected = null;
    selectedMoves = [];

    try {

        state = await request("/move", {
            method: "POST",

            body: JSON.stringify({
                fromRow: fromRow,
                fromCol: fromCol,

                toRow: row,
                toCol: col
            })
        });

        render();

    } catch (error) {

        console.error(error);

        statusEl.textContent =
            "Invalid move.";

        await loadGame();
    }
}

async function selectPiece(row, col) {

    const piece = state.board[row][col];

    if (!piece || !piece.startsWith("w")) {
        return;
    }

    try {

        const moves = await request(
            `/legal?row=${row}&col=${col}`
        );

        selected = {
            row,
            col
        };

        selectedMoves =
            Array.isArray(moves)
                ? moves
                : [];

        render();

        /*
         * Helpful message when a bishop is blocked
         */
        if (
            piece === "wB" &&
            selectedMoves.length === 0
        ) {

            statusEl.textContent =
                "This bishop is blocked. Move the pawn in front of it first.";

            return;
        }

        if (selectedMoves.length === 0) {

            statusEl.textContent =
                "This piece has no legal moves.";

        }
    } catch (error) {

        console.error(error);

        statusEl.textContent =
            "Could not calculate legal moves.";
    }
}

async function startGame() {

    try {

        selected = null;
        selectedMoves = [];

        const elo = Number(eloEl.value);

        state = await request("/new", {
            method: "POST",

            body: JSON.stringify({
                elo
            })
        });

        render();

    } catch (error) {

        console.error(error);

        statusEl.textContent =
            "Could not start a new game.";
    }
}

document
    .getElementById("newGame")
    .addEventListener("click", startGame);

document
    .getElementById("startGame")
    .addEventListener("click", startGame);

loadGame();