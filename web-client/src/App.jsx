import { useEffect, useState } from "react";
import QueuePage from "./pages/QueuePage";
import SeatPage from "./pages/SeatPage";
import { routerService } from "./services/RouterService";
import { sessionStore } from "./services/SessionStore";

export default function App() {
  const [location, setLocation] = useState(routerService.currentLocation());

  useEffect(() => routerService.subscribe(setLocation), []);

  if (location.pathname === "/queue") {
    return <QueuePage search={location.search} routerService={routerService} sessionStore={sessionStore} />;
  }

  return <SeatPage search={location.search} routerService={routerService} sessionStore={sessionStore} />;
}
