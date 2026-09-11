import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import { TrackerApp } from "@/components/TrackerApp";
import "@/styles/globals.css";
import { FontApplier } from "@/components/FontApplier";
import { TooltipProvider } from "@/components/ui/tooltip";
// import { injectMockDpsData } from "./utils/mockDpsData";
// injectMockDpsData();

const isTrackerWindow = window.location.hash === "#tracker";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <TooltipProvider delayDuration={150}>
      <FontApplier />
      {isTrackerWindow ? <TrackerApp /> : <App />}
    </TooltipProvider>
  </StrictMode>,
);
