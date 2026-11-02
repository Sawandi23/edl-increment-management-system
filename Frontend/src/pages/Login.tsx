// src/pages/Login.tsx
import { useEffect, useState } from 'react'
import { Eye, EyeOff, Lock, Shield, User } from 'lucide-react'

interface LoginProps {
  onLogin: (username: string, password: string) => Promise<void>
  onSignup: (username: string, displayName: string, password: string) => Promise<void>
}

export default function Login({ onLogin, onSignup }: LoginProps) {
  const loginSlides = [
    '/login-slide-1.png',
    '/login-slide-2.png',
    '/login-slide-3.png',
    '/login-slide-4.png',
  ]
  const [activeSlide, setActiveSlide] = useState(0)
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [showPass, setShowPass] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const timer = window.setInterval(() => {
      setActiveSlide(current => (current + 1) % loginSlides.length)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [loginSlides.length])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password || (mode === 'signup' && !displayName)) { setError('Please complete all fields'); return }
    if (mode === 'signup' && password.length < 6) { setError('Password must be at least 6 characters'); return }
    setLoading(true)
    setError('')
    try {
      if (mode === 'login') await onLogin(username, password)
      else await onSignup(username, displayName, password)
    } catch (e: any) {
      setError(e.message || 'Unable to sign in. Please check your credentials.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page" style={{ background: '#F1F4F9', minHeight: '100vh' }}>

      {/* ── Left branding panel ── */}
      <div className="login-brand" style={{
        background: 'linear-gradient(145deg, var(--maroon-950) 0%, var(--maroon-800) 44%, var(--navy-800) 100%)',
        position: 'relative', overflow: 'hidden', padding: '48px',
        display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
      }}>
        <img
          src={loginSlides[activeSlide]}
          alt=""
          aria-hidden="true"
          style={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            opacity: 0.2,
            transition: 'opacity 0.8s ease-in-out',
            pointerEvents: 'none',
          }}
        />
        {/* Grid pattern overlay */}
        <div style={{
          position: 'absolute', inset: 0, opacity: 0.04,
          backgroundImage: 'linear-gradient(rgba(255,255,255,0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.3) 1px, transparent 1px)',
          backgroundSize: '40px 40px',
        }} />

        {/* Glow circles */}
        <div style={{ position: 'absolute', top: -80, right: -80, width: 320, height: 320, borderRadius: '50%', background: 'radial-gradient(circle, rgba(56,130,220,0.18) 0%, transparent 70%)' }} />
        <div style={{ position: 'absolute', bottom: -60, left: -60, width: 260, height: 260, borderRadius: '50%', background: 'radial-gradient(circle, rgba(232,160,32,0.12) 0%, transparent 70%)' }} />

        {/* Logo */}
        <div style={{ position: 'relative' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 48 }}>
          <img src="/edl-logo.png" alt="Electricity Distribution Lanka" style={{ width: 100, height: 100, objectFit: 'contain', borderRadius: 10, background: '#fff', padding: 5 }} />
          <div>
            <div style={{ fontSize: 25, fontWeight: 700, color: '#fff', lineHeight: 1.2 }}>
              Electricity Distribution Lanka (Private) Limited
            </div>
              <div style={{ fontSize: 18, color: 'rgba(255,255,255,0.5)', marginTop: 2 }}>
               Director (Colombo City) 
              </div>
            </div>
          </div>

          <div style={{ marginBottom: 48 }}>
            <h1 style={{ fontSize: 45, fontWeight: 800, color: '#fff', lineHeight: 1.15, marginBottom: 14, letterSpacing: '-0.5px' }}>
              Salary Increment Management System
            </h1>
            <h2 style={{ fontSize: 35, fontWeight: 600, color: 'rgba(255,255,255,0.7)', lineHeight: 1.3 }}>
              HR UNIT - Colombo City
            </h2>
          </div>

          
        </div>

        {/* Footer */}
        <div style={{ position: 'relative', fontSize: 11.5, color: 'rgba(255,255,255,0.3)', marginTop: 40 }}>
          Human Resources Division &nbsp;•&nbsp; Internal System &nbsp;•&nbsp; Confidential
        </div>
      </div>

      {/* ── Right login panel ── */}
      <div style={{
        background: '#d0cdcd',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '48px 52px',
        boxShadow: '-4px 0 40px rgba(0,0,0,0.06)',
      }}>
        <div style={{ width: '100%', maxWidth: 360, animation: 'fadeIn 0.3s ease' }}>

          <div style={{ marginBottom: 36 }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 22 }}>
              <img src="/edl-logo.png" alt="EDL" style={{ width: 118, height: 118, objectFit: 'contain', borderRadius: 24, background: '#fff', padding: 10, boxShadow: '0 12px 30px rgba(65,0,25,0.12)' }} />
            </div>
            <div style={{
              width: 52, height: 52, borderRadius: 14, marginBottom: 20,
              background: 'var(--maroon-100)', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Lock size={22} strokeWidth={2} color="var(--maroon-700)" />
            </div>
            <h2 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', marginBottom: 6, letterSpacing: '-0.3px' }}>
              {mode === 'login' ? 'Welcome Back !' : 'Create your account'}
            </h2>
            <p style={{ fontSize: 13.5, color: '#06162b' }}>
              {mode === 'login' ? 'Sign in to your increment management workspace' : 'Create a secure account to access the workspace'}
            </p>
          </div>

          <form onSubmit={handleSubmit}>
            {mode === 'signup' && <div style={{ marginBottom: 18 }}>
              <label className="field-label">Full name</label>
              <input className="input" placeholder="Enter your full name" value={displayName} onChange={e => { setDisplayName(e.target.value); setError('') }} autoComplete="name" />
            </div>}
            <div style={{ marginBottom: 18 }}>
              <label className="field-label">Username</label>
              <div className="input-wrap">
                <User size={15} className="input-icon-l" />
                <input
                  className="input has-icon-l"
                  placeholder="Enter username"
                  value={username}
                  onChange={e => { setUsername(e.target.value); setError('') }}
                  autoComplete="username"
                  autoFocus
                />
              </div>
            </div>

            <div style={{ marginBottom: 24 }}>
              <label className="field-label">Password</label>
              <div className="input-wrap">
                <Lock size={15} className="input-icon-l" />
                <input
                  className="input has-icon-l"
                  type={showPass ? 'text' : 'password'}
                  placeholder={mode === 'signup' ? 'At least 6 characters' : 'Enter password'}
                  value={password}
                  onChange={e => { setPassword(e.target.value); setError('') }}
                  autoComplete="current-password"
                  style={{ paddingRight: 40 }}
                />
                <button
                  type="button"
                  onClick={() => setShowPass(v => !v)}
                  style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 2 }}
                >
                  {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            {error && (
              <div style={{
                background: 'var(--danger-bg)', color: 'var(--danger)',
                border: '1px solid #222f71',
                borderRadius: 8, padding: '10px 14px',
                fontSize: 13, fontWeight: 500, marginBottom: 18,
                display: 'flex', alignItems: 'center', gap: 8,
              }}>
                <Shield size={14} strokeWidth={2.2} style={{ flexShrink: 0 }} />
                {error}
              </div>
            )}

            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading}
              style={{ width: '100%', padding: '11px 16px', fontSize: 14, fontWeight: 600, borderRadius: 9 }}
            >
              {loading ? (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ width: 14, height: 14, border: '2px solid rgba(255,255,255,0.4)', borderTopColor: '#fff', borderRadius: '50%', animation: 'spin 0.8s linear infinite', display: 'inline-block' }} />
                  Signing in…
                </span>
              ) : mode === 'login' ? 'Sign in' : 'Create account'}
            </button>
          </form>

          <div style={{
            marginTop: 32, padding: '14px 16px', borderRadius: 10,
            background: '#f8f9ff', border: '1px solid #ecd5f3',
          }}>
            <div style={{ fontSize: 11.5, fontWeight: 700, color: 'var(--maroon-700)', marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.6px' }}>
              {mode === 'login' ? 'New to the workspace?' : 'Already have an account?'}
            </div>
            <button type="button" className="btn btn-ghost" style={{ color: 'var(--maroon-700)', padding: 0, fontSize: 12.5 }} onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); setError('') }}>
              {mode === 'login' ? 'Create an account' : 'Return to sign in'}
            </button>
          </div>

          <div style={{ marginTop: 28, textAlign: 'center', fontSize: 12, color: '#06080c' }}>
            Human Resources Division · CEB Colombo City
          </div>
        </div>
      </div>
    </div>
  )
}
