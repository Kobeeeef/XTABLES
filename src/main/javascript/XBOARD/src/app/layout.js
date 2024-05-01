import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata = {
  title: "XBOARD - XBOT",
  description: "XTABLES VIEWER",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
    <body className={inter.className}>
    <link rel="icon" href="/favicon.ico" sizes="any"/>
    {children}</body>
    </html>
  );
}
