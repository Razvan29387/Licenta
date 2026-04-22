import React from 'react';

const Pagination = ({ currentPage, totalPages, onPageChange }) => {
  if (totalPages <= 1) {
    return null; // Don't render pagination if there's only one page
  }

  const styles = {
    paginationContainer: {
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      gap: '10px',
      marginTop: '40px',
      padding: '20px',
      fontFamily: "'Segoe UI', sans-serif",
    },
    button: {
      padding: '10px 15px',
      border: '1px solid #ddd',
      borderRadius: '6px',
      backgroundColor: 'white',
      cursor: 'pointer',
      fontWeight: '600',
      color: '#333',
      transition: 'background-color 0.2s, color 0.2s',
    },
    buttonDisabled: {
      padding: '10px 15px',
      border: '1px solid #eee',
      borderRadius: '6px',
      backgroundColor: '#f9f9f9',
      cursor: 'not-allowed',
      color: '#aaa',
    },
    pageInfo: {
      fontWeight: 'bold',
      color: '#555',
    },
  };

  const handleFirst = () => onPageChange(0);
  const handlePrevious = () => onPageChange(currentPage - 1);
  const handleNext = () => onPageChange(currentPage + 1);
  const handleLast = () => onPageChange(totalPages - 1);

  const isFirstPage = currentPage === 0;
  const isLastPage = currentPage === totalPages - 1;

  return (
    <div style={styles.paginationContainer}>
      <button 
        style={isFirstPage ? styles.buttonDisabled : styles.button} 
        onClick={handleFirst} 
        disabled={isFirstPage}
      >
        First
      </button>
      <button 
        style={isFirstPage ? styles.buttonDisabled : styles.button} 
        onClick={handlePrevious} 
        disabled={isFirstPage}
      >
        Previous
      </button>
      <span style={styles.pageInfo}>
        Page {currentPage + 1} of {totalPages}
      </span>
      <button 
        style={isLastPage ? styles.buttonDisabled : styles.button} 
        onClick={handleNext} 
        disabled={isLastPage}
      >
        Next
      </button>
      <button 
        style={isLastPage ? styles.buttonDisabled : styles.button} 
        onClick={handleLast} 
        disabled={isLastPage}
      >
        Last
      </button>
    </div>
  );
};

export default Pagination;