import { registerRootComponent } from 'expo';
import App from './App';

// Keep the entry local to this workspace package so Metro always loads apps/mobile/App.js.
registerRootComponent(App);
