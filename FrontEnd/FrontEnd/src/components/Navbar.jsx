import React from 'react';
import { Link } from 'react-router-dom';

const Navbar = () => {
  const navStyle = {
    padding: '15px 30px',
    backgroundColor: 'transparent', // Fundal transparent pentru a se integra cu gradientul paginii
    width: '100%',
    textAlign: 'center', // Centrează conținutul
    boxSizing: 'border-box',
    marginBottom: '-20px', // Reduce spațiul dintre navbar și cardul principal
  };

  const brandStyle = {
    fontSize: '24px',
    fontWeight: 'bold',
    color: '#34495e', // O culoare mai sobră
    textDecoration: 'none',
    display: 'inline-block',
    transition: 'color 0.2s',
  };

  return (
    <nav style={navStyle}>
      <Link 
        to="/" 
        style={brandStyle}
        onMouseOver={(e) => e.target.style.color = '#007bff'}
        onMouseOut={(e) => e.target.style.color = '#34495e'}
      >
        JobPortal
      </Link>
    </nav>
  );
};

export default Navbar;