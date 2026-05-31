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
      role: [role] // Send role as an array
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

const getCurrentUser = () => {
  const userStr = localStorage.getItem("user");
  if(userStr) return JSON.parse(userStr);
  return null;
};

const authService = {
  register,
  login,
  logout,
  getCurrentUser,
};

export default authService;
