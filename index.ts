import { registerRootComponent } from 'expo';
import ReactNativeForegroundService from '@supersami/rn-foreground-service';
import App from './App';

// Register the foreground service to be able to run headless tasks
ReactNativeForegroundService.register({
  config: {
    alert: true,
    onServiceErrorCallBack: function () {
      console.warn('Error en el servicio de Foreground');
    },
  },
});

// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(App);
