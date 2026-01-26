const PREFIX = "[twitch-adblock]";

type level = "log" | "info" | "warn" | "error" | "debug";

function write(level: level, ...args: unknown[]) {
  const fn = console[level] ?? console.log;
  fn(`${PREFIX}`, ...args);
}

export const logger = {
  log: (...args: unknown[]) => write("log", ...args),
  info: (...args: unknown[]) => write("info", ...args),
  warn: (...args: unknown[]) => write("warn", ...args),
  error: (...args: unknown[]) => write("error", ...args),
  debug: (...args: unknown[]) => write("debug", ...args),
};