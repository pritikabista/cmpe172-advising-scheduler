package edu.sjsu.scheduler.mocknotify;

/** Lets us simulate failures: UP = normal, DOWN = returns 503, SLOW = answers after a long delay. */
public enum MockMode { UP, DOWN, SLOW }
