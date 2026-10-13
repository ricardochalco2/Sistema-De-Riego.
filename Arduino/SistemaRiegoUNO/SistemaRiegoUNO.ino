#include <SoftwareSerial.h>

// HC-05 TXD -> D10 (RX). HC-05 RXD <- D11 (TX) CON DIVISOR DE VOLTAJE.
SoftwareSerial bt(10, 11);
const int sensorPin = A0;
const int relayPin = 7;
const bool RELAY_ACTIVE_LOW = true; // Ajustar al módulo de relé
const int dryRaw = 800; // Calibrar con tierra seca
const int wetRaw = 350; // Calibrar con tierra húmeda
const int thresholdOn = 35;
const int thresholdOff = 50;
bool automaticMode = false;
bool pumpOn = false;
unsigned long lastRead = 0;
String command = "";

void setPump(bool on) {
  pumpOn = on;
  digitalWrite(relayPin, RELAY_ACTIVE_LOW ? (on ? LOW : HIGH) : (on ? HIGH : LOW));
}

int humidity() {
  int raw = analogRead(sensorPin);
  return constrain(map(raw, dryRaw, wetRaw, 0, 100), 0, 100);
}

void reportStatus(int h) {
  bt.print("HUM:"); bt.print(h);
  bt.print(";PUMP:"); bt.print(pumpOn ? "ON" : "OFF");
  bt.print(";MODE:"); bt.println(automaticMode ? "AUTO" : "MANUAL");
}

void handleCommand(String c) {
  c.trim();
  c.toUpperCase();
  if (c == "ON") {
    automaticMode = false; // Control manual tiene prioridad
    setPump(true);
  } else if (c == "OFF") {
    automaticMode = false; // Parada manual
    setPump(false);
  } else if (c == "AUTO") {
    automaticMode = true;
  } else if (c == "MANUAL") {
    automaticMode = false;
    setPump(false);
  } else if (c != "STATUS") {
    bt.println("ERROR:COMANDO");
    return;
  }
  reportStatus(humidity());
}

void setup() {
  digitalWrite(relayPin, RELAY_ACTIVE_LOW ? HIGH : LOW);
  pinMode(relayPin, OUTPUT);
  setPump(false);
  Serial.begin(9600);
  bt.begin(9600);
  reportStatus(humidity());
}

void loop() {
  while (bt.available()) {
    char ch = (char)bt.read();
    if (ch == '\n' || ch == '\r') {
      if (command.length()) { handleCommand(command); command = ""; }
    } else if (command.length() < 32) command += ch;
  }
  if (millis() - lastRead >= 1000) {
    lastRead = millis();
    int h = humidity();
    if (automaticMode) {
      if (!pumpOn && h < thresholdOn) setPump(true);
      else if (pumpOn && h >= thresholdOff) setPump(false);
    }
    reportStatus(h);
  }
}
