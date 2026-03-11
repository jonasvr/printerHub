// sockjs-client expects Node's `global` — polyfill it for the browser
(window as any).global = window;

import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

/**
 * Angular 17 "standalone" bootstrap — no NgModule needed.
 * All providers (router, HTTP client, etc.) are registered in app.config.ts.
 */
bootstrapApplication(AppComponent, appConfig)
  .catch(err => console.error(err));
