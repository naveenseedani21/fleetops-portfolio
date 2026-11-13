import { bootstrapApplication } from '@angular/platform-browser';
import { Preview } from './preview/preview';

// Separate entry point: no OAuth, HTTP client, API provider or application router.
bootstrapApplication(Preview).catch((error: unknown) => console.error(error));
