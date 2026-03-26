import React, { useState, useEffect } from 'react';
import FirmTable from '../components/FirmTable';
import Pagination from '../components/Pagination';

const FirmePage = () => {
  const [firms, setFirms] = useState([]);
  const [error, setError] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  
  // State pentru paginare
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage] = useState(10);

  useEffect(() => {
    const loadAllFirms = async () => {
      try {
        // Updated endpoint to include /api prefix to match proxy configuration
        const response = await fetch('/api/firme');
        if (!response.ok) {
          throw new Error(`Server responded with ${response.status}`);
        }
        
        const contentType = response.headers.get("content-type");
        if (contentType && contentType.indexOf("application/json") !== -1) {
            const data = await response.json();
            setFirms(data);
        } else {
            throw new Error("Received non-JSON response from server");
        }
      } catch (err) {
        setError(err.message);
        console.error("Failed to load firms:", err);
      } finally {
        setIsLoading(false);
      }
    };

    loadAllFirms();
  }, []);

  // Calculăm firmele pentru pagina curentă
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentFirms = firms.slice(indexOfFirstItem, indexOfLastItem);

  // Funcție pentru a schimba pagina
  const paginate = (pageNumber) => setCurrentPage(pageNumber);

  return (
    <div style={{ padding: '20px' }}>
      <h1>Firme</h1>
      {error ? (
        <p className="error" style={{color: 'red'}}>Error loading firms: {error}. Are you sure the backend is running and the /api/firme endpoint exists?</p>
      ) : (
        <>
          <FirmTable firms={currentFirms} isLoading={isLoading} />
          <Pagination itemsPerPage={itemsPerPage} totalItems={firms.length} paginate={paginate} currentPage={currentPage} />
        </>
      )}
    </div>
  );
};

export default FirmePage;