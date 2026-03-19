import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 32, fontFamily: 'monospace', color: '#c00' }}>
          <h2>⚠️ App failed to render</h2>
          <pre style={{ whiteSpace: 'pre-wrap', background: '#fff3f3', padding: 16, borderRadius: 8 }}>
            {String(this.state.error)}
          </pre>
          <p>Open browser DevTools → Console for full stack trace.</p>
        </div>
      );
    }
    return this.props.children;
  }
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
);
