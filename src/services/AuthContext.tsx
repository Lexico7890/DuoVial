import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { signIn, signUp, signOut, getCurrentUser, confirmSignUp } from 'aws-amplify/auth';
import { AUTH_CONFIGURED } from './AuthConfig';

type AuthUser = {
  username: string;
  email?: string;
} | null;

type AuthContextType = {
  user: AuthUser;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; message: string }>;
  signup: (email: string, password: string) => Promise<{ success: boolean; message: string }>;
  confirmSignUpCode: (email: string, code: string) => Promise<{ success: boolean; message: string }>;
  logout: () => Promise<void>;
  authConfigured: boolean;
};

const AuthContext = createContext<AuthContextType>({
  user: null,
  loading: false,
  login: async () => ({ success: false, message: 'Auth no configurado' }),
  signup: async () => ({ success: false, message: 'Auth no configurado' }),
  confirmSignUpCode: async () => ({ success: false, message: 'Auth no configurado' }),
  logout: async () => {},
  authConfigured: false,
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!AUTH_CONFIGURED) {
      setLoading(false);
      return;
    }
    getCurrentUser()
      .then((cognitoUser) => {
        setUser({
          username: cognitoUser.username,
          email: cognitoUser.signInDetails?.loginId,
        });
      })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    if (!AUTH_CONFIGURED) return { success: false, message: 'Cognito no configurado. Completa AuthConfig.ts' };
    try {
      const result = await signIn({ username: email, password });
      if (result.isSignedIn) {
        setUser({ username: email, email });
        return { success: true, message: 'Inicio de sesión exitoso' };
      }
      return { success: false, message: 'Inicio de sesión incompleto' };
    } catch (err: any) {
      return { success: false, message: err?.message || 'Error al iniciar sesión' };
    }
  }, []);

  const signupFn = useCallback(async (email: string, password: string) => {
    if (!AUTH_CONFIGURED) return { success: false, message: 'Cognito no configurado. Completa AuthConfig.ts' };
    try {
      await signUp({ username: email, password, options: { userAttributes: { email } } });
      return { success: true, message: 'Código de verificación enviado a tu correo' };
    } catch (err: any) {
      return { success: false, message: err?.message || 'Error al registrarse' };
    }
  }, []);

  const confirmSignUpCodeFn = useCallback(async (email: string, code: string) => {
    if (!AUTH_CONFIGURED) return { success: false, message: 'Cognito no configurado' };
    try {
      await confirmSignUp({ username: email, confirmationCode: code });
      return { success: true, message: 'Cuenta verificada exitosamente' };
    } catch (err: any) {
      return { success: false, message: err?.message || 'Código inválido' };
    }
  }, []);

  const logout = useCallback(async () => {
    if (!AUTH_CONFIGURED) return;
    try {
      await signOut();
      setUser(null);
    } catch (err) {
      console.warn('Error al cerrar sesión:', err);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, signup: signupFn, confirmSignUpCode: confirmSignUpCodeFn, logout, authConfigured: AUTH_CONFIGURED }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
