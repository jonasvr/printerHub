import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root shell component — just renders the router outlet.
 * Navigation, sidebars, and header will be added in a later phase.
 * For now the router renders directly into the full-screen layout.
 */
@Component({
  selector: 'ph-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`
})
export class AppComponent {}
