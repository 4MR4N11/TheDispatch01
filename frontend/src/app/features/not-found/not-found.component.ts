import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="not-found-container">
      <div class="not-found-content">
        <div class="error-code">404</div>
        <h1>Page Not Found</h1>
        <p>The page you're looking for doesn't exist or has been moved.</p>
        <a routerLink="/home" class="home-button">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
            <polyline points="9 22 9 12 15 12 15 22"></polyline>
          </svg>
          Back to Home
        </a>
      </div>
    </div>
  `,
  styles: [`
    .not-found-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
      padding: 20px;
    }

    .not-found-content {
      text-align: center;
      color: #fff;
    }

    .error-code {
      font-size: 150px;
      font-weight: 700;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      line-height: 1;
      margin-bottom: 20px;
    }

    h1 {
      font-size: 32px;
      font-weight: 600;
      margin: 0 0 16px 0;
      color: #fff;
    }

    p {
      font-size: 18px;
      color: #a0aec0;
      margin: 0 0 32px 0;
    }

    .home-button {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 14px 28px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: #fff;
      text-decoration: none;
      border-radius: 8px;
      font-weight: 500;
      font-size: 16px;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .home-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
    }

    .home-button svg {
      width: 20px;
      height: 20px;
    }

    @media (max-width: 480px) {
      .error-code {
        font-size: 100px;
      }

      h1 {
        font-size: 24px;
      }

      p {
        font-size: 16px;
      }
    }
  `]
})
export class NotFoundComponent {}
