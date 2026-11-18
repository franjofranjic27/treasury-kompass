import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CurrentClient } from './core/current-client';
import { readUseMock, writeUseMock } from './core/data-mode';
import { ChatPanel } from './shell/chat-panel';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ChatPanel],
  template: `
    <header class="topbar">
      <a class="brand" routerLink="/">Treasury Kompass</a>
      <nav class="topnav">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Kunden</a>
        @if (currentClient.id(); as id) {
          <a [routerLink]="['/clients', id, 'profilierung']" routerLinkActive="active">Profilierung</a>
          <a [routerLink]="['/clients', id]" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Dashboard</a>
          <a [routerLink]="['/clients', id, 'beratung']" routerLinkActive="active">Beratung</a>
        }
      </nav>
      <label class="toggle">
        <input type="checkbox" [checked]="useMock" (change)="toggleMock()" />
        Demo-Daten
      </label>
    </header>
    <div class="layout">
      <main class="content"><router-outlet /></main>
      <app-chat-panel />
    </div>
    <footer class="footer">Hackathon-Prototyp · Beispieldaten erfundener Firmen</footer>
  `
})
export class App {
  protected readonly currentClient = inject(CurrentClient);
  protected readonly useMock = readUseMock();

  protected toggleMock(): void {
    writeUseMock(!this.useMock);
    location.assign('/');
  }
}
