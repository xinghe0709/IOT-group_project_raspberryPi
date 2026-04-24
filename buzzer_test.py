import RPi.GPIO as GPIO
import time

BUZZER_PIN = 17

GPIO.setmode(GPIO.BCM)
GPIO.setwarnings(False)
GPIO.setup(BUZZER_PIN, GPIO.OUT)

print("Buzzer test — beep moi 10 giay. Ctrl+C de dung.")

try:
    while True:
        GPIO.output(BUZZER_PIN, GPIO.HIGH)
        time.sleep(0.1)
        GPIO.output(BUZZER_PIN, GPIO.LOW)
        print("beep!")
        time.sleep(10)
except KeyboardInterrupt:
    print("\nDung.")
finally:
    GPIO.cleanup()
