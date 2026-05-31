import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import authService from '../services/auth.service';
import '../styles/AuthPage.css';

const SignupPage = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('user'); // Default role
  const [successful, setSuccessful] = useState(false);
  const [message, setMessage] = useState('');
  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();
    setMessage('');
    setSuccessful(false);

    try {
      const response = await authService.register(username, email, password, role);
      if (response.ok) {
          setMessage('Registration successful! You can now log in.');
          setSuccessful(true);
          setTimeout(() => {
              navigate("/login");
          }, 2000);
      } else {
          const errorData = await response.json();
          setMessage(errorData.message || 'Registration failed.');
      }
    } catch (error) {
      setMessage(error.message || 'An error occurred during registration.');
      setSuccessful(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2>Sign Up</h2>
        <form onSubmit={handleRegister}>
          {!successful && (
            <div>
              <div className="form-group">
                <label htmlFor="username">Username</label>
                <input
                  type="text"
                  className="form-control"
                  name="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="email">Email</label>
                <input
                  type="email"
                  className="form-control"
                  name="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="password">Password</label>
                <input
                  type="password"
                  className="form-control"
                  name="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="role">Account Type</label>
                <select 
                    className="form-control" 
                    value={role} 
                    onChange={(e) => setRole(e.target.value)}
                >
                    <option value="user">Job Seeker</option>
                    <option value="company">Company</option>
                </select>
              </div>

              <div className="form-group">
                <button className="btn-primary">Sign Up</button>
              </div>
            </div>
          )}

          {message && (
            <div className="form-group">
              <div
                className={ successful ? "alert alert-success" : "alert alert-danger" }
                role="alert"
              >
                {message}
              </div>
            </div>
          )}
        </form>
        {!successful && (
            <p className="auth-link">
                Already have an account? <Link to="/login">Log in here</Link>
            </p>
        )}
      </div>
    </div>
  );
};

export default SignupPage;