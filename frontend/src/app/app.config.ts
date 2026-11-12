import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApiConfiguration } from './api';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(),
    // The contract declares the server as "/", which the generated client would concatenate
    // into "//api/...", a protocol-relative URL pointing at a host called "api". An empty root
    // keeps the calls on the current origin: the dev-server proxy now, the packaged desktop
    // application later.
    provideApiConfiguration(''),
  ],
};
