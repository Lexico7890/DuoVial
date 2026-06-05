import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, ActivityIndicator, ScrollView, KeyboardAvoidingView, Platform,
} from 'react-native';
import { colors } from '../theme/colors';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useAuth } from '../services/AuthContext';

type AuthMode = 'login' | 'signup' | 'confirm';

export function LoginScreen({ onClose }: { onClose: () => void }) {
  const { login, signup, confirmSignUpCode, authConfigured } = useAuth();

  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const clearMessages = () => { setError(''); setSuccessMsg(''); };

  const handleLogin = async () => {
    clearMessages();
    if (!email.trim()) { setError('Ingresa tu correo'); return; }
    if (!password) { setError('Ingresa tu contraseña'); return; }
    setLoading(true);
    const result = await login(email.trim(), password);
    setLoading(false);
    if (result.success) {
      onClose();
    } else {
      setError(result.message);
    }
  };

  const handleSignup = async () => {
    clearMessages();
    if (!email.trim()) { setError('Ingresa tu correo'); return; }
    if (password.length < 6) { setError('La contraseña debe tener al menos 6 caracteres'); return; }
    if (password !== confirmPassword) { setError('Las contraseñas no coinciden'); return; }
    setLoading(true);
    const result = await signup(email.trim(), password);
    setLoading(false);
    if (result.success) {
      setSuccessMsg(result.message);
      setMode('confirm');
    } else {
      setError(result.message);
    }
  };

  const handleConfirm = async () => {
    clearMessages();
    if (!code.trim()) { setError('Ingresa el código de verificación'); return; }
    setLoading(true);
    const result = await confirmSignUpCode(email.trim(), code.trim());
    setLoading(false);
    if (result.success) {
      setSuccessMsg('Cuenta verificada. Ahora puedes iniciar sesión.');
      setTimeout(() => { setMode('login'); setCode(''); }, 1500);
    } else {
      setError(result.message);
    }
  };

  const renderForm = () => {
    if (mode === 'confirm') {
      return (
        <>
          <Text style={styles.title}>Verificar Código</Text>
          <Text style={styles.subtitle}>Ingresa el código enviado a {email}</Text>

          <TextInput
            style={styles.input}
            placeholder="Código de verificación"
            placeholderTextColor={colors.textSecondary}
            value={code}
            onChangeText={setCode}
            keyboardType="number-pad"
          />

          {error ? <Text style={styles.errorText}>{error}</Text> : null}
          {successMsg ? <Text style={styles.successText}>{successMsg}</Text> : null}

          <TouchableOpacity style={styles.primaryButton} onPress={handleConfirm} disabled={loading}>
            {loading ? <ActivityIndicator color="#000" /> : <Text style={styles.primaryButtonText}>VERIFICAR</Text>}
          </TouchableOpacity>

          <TouchableOpacity onPress={() => { setMode('login'); setCode(''); clearMessages(); }}>
            <Text style={styles.linkText}>Volver a inicio de sesión</Text>
          </TouchableOpacity>
        </>
      );
    }

    if (mode === 'signup') {
      return (
        <>
          <Text style={styles.title}>Crear Cuenta</Text>
          <Text style={styles.subtitle}>Regístrate para acceder a todas las funciones</Text>

          <TextInput
            style={styles.input}
            placeholder="Correo electrónico"
            placeholderTextColor={colors.textSecondary}
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
          />
          <View style={styles.passwordContainer}>
            <TextInput
              style={styles.passwordInput}
              placeholder="Contraseña"
              placeholderTextColor={colors.textSecondary}
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPassword}
            />
            <TouchableOpacity onPress={() => setShowPassword(!showPassword)} style={styles.eyeButton}>
              <MaterialCommunityIcons name={showPassword ? 'eye-off' : 'eye'} size={22} color={colors.textSecondary} />
            </TouchableOpacity>
          </View>
          <TextInput
            style={styles.input}
            placeholder="Confirmar contraseña"
            placeholderTextColor={colors.textSecondary}
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            secureTextEntry={!showPassword}
          />

          {error ? <Text style={styles.errorText}>{error}</Text> : null}

          <TouchableOpacity style={styles.primaryButton} onPress={handleSignup} disabled={loading}>
            {loading ? <ActivityIndicator color="#000" /> : <Text style={styles.primaryButtonText}>REGISTRARSE</Text>}
          </TouchableOpacity>

          <TouchableOpacity onPress={() => { setMode('login'); clearMessages(); }}>
            <Text style={styles.linkText}>¿Ya tienes cuenta? Inicia sesión</Text>
          </TouchableOpacity>
        </>
      );
    }

    return (
      <>
        <View style={styles.logoContainer}>
          <MaterialCommunityIcons name="shield-account" size={48} color={colors.neonGreen} />
        </View>
        <Text style={styles.title}>Iniciar Sesión</Text>
        <Text style={styles.subtitle}>Accede a tu cuenta de DuoVial</Text>

        <TextInput
          style={styles.input}
          placeholder="Correo electrónico"
          placeholderTextColor={colors.textSecondary}
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <View style={styles.passwordContainer}>
          <TextInput
            style={styles.passwordInput}
            placeholder="Contraseña"
            placeholderTextColor={colors.textSecondary}
            value={password}
            onChangeText={setPassword}
            secureTextEntry={!showPassword}
          />
          <TouchableOpacity onPress={() => setShowPassword(!showPassword)} style={styles.eyeButton}>
            <MaterialCommunityIcons name={showPassword ? 'eye-off' : 'eye'} size={22} color={colors.textSecondary} />
          </TouchableOpacity>
        </View>

        {error ? <Text style={styles.errorText}>{error}</Text> : null}

        <TouchableOpacity style={styles.primaryButton} onPress={handleLogin} disabled={loading}>
          {loading ? <ActivityIndicator color="#000" /> : <Text style={styles.primaryButtonText}>INICIAR SESIÓN</Text>}
        </TouchableOpacity>

        {!authConfigured && (
          <View style={styles.demoBanner}>
            <MaterialCommunityIcons name="information-outline" size={16} color={colors.amber} />
            <Text style={styles.demoBannerText}>
              Modo demo: configura AWS Cognito en src/services/AuthConfig.ts
            </Text>
          </View>
        )}

        <TouchableOpacity onPress={() => { setMode('signup'); clearMessages(); }}>
          <Text style={styles.linkText}>¿No tienes cuenta? Regístrate</Text>
        </TouchableOpacity>
      </>
    );
  };

  return (
    <View style={styles.overlay}>
      <KeyboardAvoidingView behavior={Platform.OS === 'android' ? undefined : 'padding'} style={styles.centered}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <TouchableOpacity style={styles.closeButton} onPress={onClose}>
              <MaterialCommunityIcons name="close" size={24} color={colors.textPrimary} />
            </TouchableOpacity>
            {renderForm()}
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  centered: {
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    width: '100%',
    alignItems: 'center',
    paddingVertical: 40,
  },
  card: {
    width: '88%',
    backgroundColor: colors.background,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 28,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 16,
    elevation: 12,
  },
  closeButton: {
    alignSelf: 'flex-end',
    marginBottom: 8,
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 22,
    fontWeight: '900',
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 12,
    color: colors.textSecondary,
    textAlign: 'center',
    marginBottom: 24,
  },
  input: {
    backgroundColor: '#0D1B2A',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 15,
    color: colors.textPrimary,
    marginBottom: 14,
  },
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#0D1B2A',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    marginBottom: 14,
  },
  passwordInput: {
    flex: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 15,
    color: colors.textPrimary,
  },
  eyeButton: {
    paddingHorizontal: 14,
  },
  primaryButton: {
    backgroundColor: colors.neonGreen,
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 6,
    marginBottom: 16,
  },
  primaryButtonText: {
    color: '#000',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  linkText: {
    color: colors.surface,
    fontSize: 13,
    fontWeight: '700',
    textAlign: 'center',
    marginTop: 8,
  },
  errorText: {
    color: colors.neonRed,
    fontSize: 12,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 10,
  },
  successText: {
    color: colors.neonGreen,
    fontSize: 12,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 10,
  },
  demoBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.amberLight,
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    gap: 8,
  },
  demoBannerText: {
    color: colors.amber,
    fontSize: 11,
    fontWeight: '600',
    flex: 1,
  },
});
