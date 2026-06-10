const API_URL = "/api/auth/";

const register = (username, email, password, role) => {
  return fetch(API_URL + "signup", {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      username,
      email,
      password,
      role: [role]
    }),
  });
};

const login = async (username, password) => {
  const response = await fetch(API_URL + "signin", {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      username,
      password,
    }),
  });

  if (response.ok) {
      const data = await response.json();
      if (data.accessToken) {
        localStorage.setItem("user", JSON.stringify(data));
      }
      return data;
  } else {
      const errorText = await response.text();
      throw new Error(errorText || "Login failed");
  }
};

const logout = () => {
  localStorage.removeItem("user");
};

const parseJwt = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));

    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

const getCurrentUser = () => {
  const userStr = localStorage.getItem("user");
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user && user.accessToken) {
        const decodedJwt = parseJwt(user.accessToken);
        
        if (decodedJwt && decodedJwt.exp * 1000 < Date.now()) {
          console.warn("Session expired. Logging out silently.");
          logout();
          return null; // Token is expired, treat user as logged out
        }
        return user;
      }
    } catch (e) {
      console.error("Error parsing user data from local storage", e);
      logout();
      return null;
    }
  }
  return null;
};

const authService = {
  register,
  login,
  logout,
  getCurrentUser,
};

export default authService;