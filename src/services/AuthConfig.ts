import { Amplify } from 'aws-amplify';

// ============================================================
// CONFIGURACIÓN DE AWS COGNITO
// ============================================================
// Para activar la autenticación, completa estos valores con
// los datos de tu User Pool en AWS Cognito:
//
//   1. Ve a AWS Console > Cognito > Create User Pool
//   2. Copia el ID del Pool (ej: "us-east-1_abc123")
//   3. Copia el Client ID (ej: "1abc2def3ghij4klm5nop6qr7")
//   4. Completa las variables abajo o usa .env:
//      EXPO_PUBLIC_COGNITO_USER_POOL_ID=us-east-1_abc123
//      EXPO_PUBLIC_COGNITO_CLIENT_ID=1abc2def3ghij4klm5nop6qr7
//
// Mientras no configures Cognito, la app funcionará sin login.
// ============================================================

const USER_POOL_ID = process.env.EXPO_PUBLIC_COGNITO_USER_POOL_ID || '';
const CLIENT_ID = process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID || '';

export const AUTH_CONFIGURED = !!(USER_POOL_ID && CLIENT_ID);

export function configureAuth() {
  if (!AUTH_CONFIGURED) return;
  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: USER_POOL_ID,
        userPoolClientId: CLIENT_ID,
      },
    },
  });
}
