
import "./globals.css";
import { PrimeReactProvider } from 'primereact/api';
import 'primereact/resources/primereact.css';
import 'primeicons/primeicons.css';
import 'primereact/resources/primereact.min.css';

export const metadata = {
  title: "XBOARD - XBOT",
  description: "XTABLES VIEWER",
};

export default function RootLayout({ children }) {
  return (
      <html lang="en" suppressHydrationWarning>
      <head>
        <link id="theme-css" href={`/themes/lara-dark-indigo/theme.css`} rel="stylesheet"></link>
      </head>
      <body>
      <PrimeReactProvider>

      {children}
      </PrimeReactProvider>
      </body>
      </html>
  );
}
