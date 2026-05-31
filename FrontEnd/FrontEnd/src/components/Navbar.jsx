import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../services/auth.service';

const Navbar = () => {
  const [currentUser, setCurrentUser] = useState(undefined);
  const navigate = useNavigate();

  useEffect(() => {
    const user = authService.getCurrentUser();
    if (user) {
      setCurrentUser(user);
    }
  }, []);

  const logOut = () => {
    authService.logout();
    setCurrentUser(undefined);
    navigate('/login');
  };

  const navStyle = {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '15px 30px',
    backgroundColor: 'transparent',
    width: '100%',
    boxSizing: 'border-box',
    marginBottom: '-20px',
  };

  const brandStyle = {
    fontSize: '24px',
    fontWeight: 'bold',
    color: '#34495e',
    textDecoration: 'none',
  };

  const linkStyle = {
    fontSize: '16px',
    fontWeight: '500',
    color: '#34495e',
    textDecoration: 'none',
    marginLeft: '20px',
    cursor: 'pointer',
    background: 'none',
    border: 'none',
    padding: 0,
    fontFamily: 'inherit',
    transition: 'color 0.2s',
  };

  return (
    <nav style={navStyle}>
      <Link to="/" style={brandStyle}>
        JobPortal
      </Link>
      <div>
        {currentUser ? (
          <>
            <span style={{ marginRight: '15px', color: '#555' }}>
              Hello, {currentUser.username}
            </span>
            {currentUser.roles && currentUser.roles.includes("ROLE_COMPANY") && (
              <Link 
                to="/company-dashboard" 
                style={linkStyle}
                onMouseOver={(e) => e.target.style.color = '#007bff'}
                onMouseOut={(e) => e.target.style.color = '#34495e'}
              >
                Dashboard
              </Link>
            )}
            {currentUser.roles && currentUser.roles.includes("ROLE_ADMIN") && (
              <Link 
                to="/admin" 
                style={linkStyle}
                onMouseOver={(e) => e.target.style.color = '#007bff'}
                onMouseOut={(e) => e.target.style.color = '#34495e'}
              >
                Admin
              </Link>
            )}
            <button 
                onClick={logOut} 
                style={linkStyle}
                onMouseOver={(e) => e.target.style.color = '#dc3545'}
                onMouseOut={(e) => e.target.style.color = '#34495e'}
            >
              LogOut
            </button>
          </>
        ) : (
          <>
            <Link 
              to="/login" 
              style={linkStyle}
              onMouseOver={(e) => e.target.style.color = '#007bff'}
              onMouseOut={(e) => e.target.style.color = '#34495e'}
            >
              Login
            </Link>
            <Link 
              to="/signup" 
              style={linkStyle}
              onMouseOver={(e) => e.target.style.color = '#007bff'}
              onMouseOut={(e) => e.target.style.color = '#34495e'}
            >
              Sign Up
            </Link>
          </>
        )}
      </div>
    </nav>
  );
};

export default Navbar;