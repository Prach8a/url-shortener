import React, { useState, useEffect, useRef } from 'react';
import './App.css';
import axios from 'axios';

const API_URL = 'http://localhost:8080/api/v1';

function App() {
  const [url, setUrl] = useState('');
  const [shortUrl, setShortUrl] = useState('');
  const [shortCode, setShortCode] = useState('');
  const [qrCode, setQrCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [stats, setStats] = useState(null);
  const intervalRef = useRef(null);

  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const fetchStats = async (code) => {
    try {
      const response = await axios.get(`${API_URL}/stats/${code}`);
      setStats(response.data);
      console.log('Stats fetched:', response.data);
      return response.data;
    } catch (err) {
      console.log('Stats not available');
      return null;
    }
  };

  const shortenUrl = async (generateQR = false) => {
    if (!url) {
      setError('Please enter a URL');
      return;
    }

    // Clean URL - DON'T encode it
    let cleanUrl = url.trim();
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      cleanUrl = 'https://' + cleanUrl;
    }

    console.log('Sending URL:', cleanUrl);

    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    try {
      setLoading(true);
      setError('');
      setCopied(false);
      setStats(null);
      setShortUrl('');
      setShortCode('');
      setQrCode('');

      const response = await axios.post(`${API_URL}/shorten`, {
        longUrl: cleanUrl,
        generateQr: generateQR,
        expiryDays: 30
      });

      const data = response.data;
      setShortUrl(data.shortUrl);
      setShortCode(data.shortCode);
      if (data.qrCodeBase64) {
        setQrCode(data.qrCodeBase64);
      }
      
      await fetchStats(data.shortCode);
      
      // Auto-refresh stats every 10 seconds
      intervalRef.current = setInterval(() => {
        console.log('Auto-refreshing stats for:', data.shortCode);
        fetchStats(data.shortCode);
      }, 10000);
      
    } catch (err) {
      console.error('Error:', err);
      setError(err.response?.data?.error || 'Failed to shorten URL. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = async () => {
    try {
      await navigator.clipboard.writeText(shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      const textarea = document.createElement('textarea');
      textarea.value = shortUrl;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="App">
      <nav className="navbar">
        <a href="/" className="navbar-brand">
          <span>;)</span> Shortly
        </a>
        <div className="navbar-links">
          <a href="#features">Features</a>
          <a href="#shortener">Shorten</a>
          <a href="#stats">Stats</a>
        </div>
      </nav>

      <section className="hero">
        <h1>Short URLs,<br />Shorter than you ;)</h1>
        <p>Transform long, messy links into clean, shareable short URLs. Track clicks, generate QR codes, and more.</p>

        <div className="container" id="shortener">
          <div className="card">
            <h2>Shorten Your URL</h2>
            <p className="card-subtitle">Paste your long URL and get a short link instantly</p>

            <div className="input-group">
              <input
                type="url"
                placeholder="https://example-your-long-url.com"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && shortenUrl(true)}
              />
            </div>

            <div className="button-group">
              <button 
                className="btn-primary" 
                onClick={() => shortenUrl(true)} 
                disabled={loading}
                style={{ width: '100%' }}
              >
                {loading ? '⏳ Shortening...' : '🔗 Shorten URL'}
              </button>
            </div>

            {error && <div className="error">{error}</div>}

            {shortUrl && (
              <div className="result">
                <div className="result-label">✅ Your shortened URL:</div>
                <div className="result-url">
                  <a 
                    href={shortUrl} 
                    target="_blank" 
                    rel="noopener noreferrer"
                  >
                    {shortUrl}
                  </a>
                  <button className="btn-copy" onClick={copyToClipboard}>
                    {copied ? '✅ Copied!' : '📋 Copy'}
                  </button>
                </div>
                <div className="result-code">🔑 Code: <span>{shortCode}</span></div>
                
                {qrCode && (
                  <div className="qr-container">
                    <img src={`data:image/png;base64,${qrCode}`} alt="QR Code" />
                  </div>
                )}

                {stats && (
                  <div className="stats">
                    <h4>📊 Statistics</h4>
                    <div className="stats-grid">
                      <div className="stat-item">
                        <span className="stat-label">Clicks</span>
                        <span className="stat-value">{stats.click_count || 0}</span>
                      </div>
                      <div className="stat-item">
                        <span className="stat-label">Created</span>
                        <span className="stat-value">
                          {stats.created_at ? new Date(stats.created_at).toLocaleDateString() : 'N/A'}
                        </span>
                      </div>
                      <div className="stat-item">
                        <span className="stat-label">Expires</span>
                        <span className="stat-value">
                          {stats.expires_at ? new Date(stats.expires_at).toLocaleDateString() : 'Never'}
                        </span>
                      </div>
                      <div className="stat-item">
                        <span className="stat-label">Status</span>
                        <span className="stat-value" style={{ 
                          color: stats.is_active ? '#10b981' : '#ef4444'
                        }}>
                          {stats.is_active ? 'Active' : 'Inactive'}
                        </span>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </section>


{/*}      <section className="features" id="features">
        <h2>⚡ Why Shortly?</h2>
        <p>Everything you need to manage your links</p>
        <div className="features-grid">
          <div className="feature-card">
            <div className="feature-icon">🔗</div>
            <h3>Instant Shortening</h3>
            <p>Paste any long URL and get a short link in milliseconds</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">📱</div>
            <h3>QR Code Generation</h3>
            <p>Every short URL comes with a scannable QR code for mobile sharing</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">📊</div>
            <h3>Click Analytics</h3>
            <p>Track how many people clicked your link in real-time</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">⚡</div>
            <h3>Blazing Fast</h3>
            <p>Redirects happen in under 100ms with our optimized infrastructure</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">🔒</div>
            <h3>Secure & Reliable</h3>
            <p>Your links are safe with SSL encryption and 99.9% uptime guarantee</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">🌐</div>
            <h3>Works Anywhere</h3>
            <p>Short URLs work on all devices, browsers, and platforms globally</p>
          </div>
        </div>
      </section>
*/}
      <footer className="footer">
        <p> Shortly. Made with ❤️</p>
      </footer>
    </div>
  );
}

export default App;