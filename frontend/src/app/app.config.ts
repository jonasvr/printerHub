import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';

/**
 * Application-level providers — the Angular 17 replacement for the root NgModule.
 *
 * provideRouter()    — registers the router with our route definitions
 * provideHttpClient() — makes HttpClient available via DI everywhere
 * provideAnimations() — needed for Angular CDK overlays / transitions
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    provideAnimations(),
  ]
};
