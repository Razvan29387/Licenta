import React from 'react';
import { Link } from 'react-router-dom';

const Navbar = () => {
  return (
    <nav style={{ padding: '15px 30px', backgroundColor: '#fff', borderBottom: '1px solid #eaeaea', marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }}>
      {/* Brand / Logo Area */}
      <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#007bff' }}>
        <Link to="/" style={{ textDecoration: 'none', color: 'inherit' }}>JobPortal</Link>
      </div>

      {/* Navigation Links */}
      <ul style={{ display: 'flex', listStyle: 'none', gap: '30px', margin: 0, padding: 0 }}>
        <li>
          <Link to="/" style={{ textDecoration: 'none', color: '#333', fontWeight: '500', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#007bff'} onMouseOut={(e) => e.target.style.color = '#333'}>Dashboard</Link>
        </li>
        <li>
          <Link to="/firme" style={{ textDecoration: 'none', color: '#333', fontWeight: '500', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#007bff'} onMouseOut={(e) => e.target.style.color = '#333'}>Firme</Link>
        </li>
        <li>
          <Link to="/jobs" style={{ textDecoration: 'none', color: '#333', fontWeight: '500', transition: 'color 0.2s' }} onMouseOver={(e) => e.target.style.color = '#007bff'} onMouseOut={(e) => e.target.style.color = '#333'}>Joburi</Link>
        </li>
      </ul>

      {/* Company / User Actions Area */}
      <div>
         <Link to="/company-dashboard" style={{ textDecoration: 'none', backgroundColor: '#28a745', color: 'white', padding: '10px 20px', borderRadius: '5px', fontWeight: 'bold', fontSize: '14px', boxShadow: '0 2px 4px rgba(40,167,69,0.3)' }}>
            For Employers
         </Link>
      </div>
    </nav>
  );
};

export default Navbar;