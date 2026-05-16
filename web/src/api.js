// WebSocket connection — single port, /ws path
const WS_BASE = import.meta.env.DEV ? 'ws://localhost:55533/ws' : `ws://${window.location.host}/ws`;

let socket = null;
const listeners = {
  status: [],
  status_delta: [],
  logs: [],
  new_log: [],
  completions: [],
  command_history: []
};

function connectWebSocket() {
  if (socket && (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN)) {
    return;
  }

  socket = new WebSocket(WS_BASE);

  socket.onopen = () => {
    console.log('WebSocket connected');
    fetchStatus();
  };

  socket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      if (msg.type && listeners[msg.type]) {
        // Unwrap data_json back into structured object for new_log
        if (msg.type === 'new_log' && msg.data_json) {
          try {
            msg.data = JSON.parse(msg.data_json);
          } catch (e) {
            msg.data = msg.data_json;
          }
        }
        listeners[msg.type].forEach(cb => cb(msg));
      }
    } catch (e) {
      console.error('Invalid WS message', e);
    }
  };

  socket.onclose = () => {
    console.log('WebSocket disconnected. Reconnecting in 3s...');
    setTimeout(connectWebSocket, 3000);
  };
}

export function on(type, callback) {
  if (!listeners[type]) {
    listeners[type] = [];
  }
  listeners[type].push(callback);
}

export function off(type, callback) {
  if (listeners[type]) {
    listeners[type] = listeners[type].filter(cb => cb !== callback);
  }
}

// Ensure WS is connected
connectWebSocket();

function send(action, payload = {}) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ action, ...payload }));
  }
}

export function fetchStatus() {
  send('fetch_status');
}

export function fetchLogs() {
  send('fetch_logs');
}

export function fetchCompletions(command, requestId) {
  send('fetch_completions', { command, requestId });
}

export function sendCommand(command) {
  send('execute_command', { command });
}

export function fetchCommandHistory() {
  send('fetch_command_history', {});
}
