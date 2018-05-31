#include <Servo.h>
#include "SoftwareSerial.h"


/**********/
/* MACROS */
/**********/

#define BLUET_RX_PIN   1
#define BLUET_TX_PIN   0

#define MOTOR1_PIN     2
#define MOTOR2_PIN     3
#define MOTOR3_PIN     4
#define MOTOR4_PIN     5

#define BT_BITRATE  9600

#define NUM_MOTORS     4


/*********************/
/* GLOBAL VARIABLES  */
/*********************/


SoftwareSerial bluetooth(BLUET_RX_PIN, BLUET_TX_PIN);
Servo motors[NUM_MOTORS];
byte last_angles[NUM_MOTORS] = {-1};
byte motors_pins[NUM_MOTORS] = {MOTOR1_PIN, MOTOR2_PIN, MOTOR3_PIN, MOTOR4_PIN};


/*************/
/*LOCAL FUNCS*/
/*************/


/*********************************************************/
/** _motors_attach                                       */
/** Inicia os servos conectando-os aos pinos utilizados. */
/** Alguns arduinos só permitem servos nos pinos 9 e 10. */
/** Ver: https://www.arduino.cc/en/Reference/ServoAttach */
/*********************************************************/
void _motors_attach() {
    for (byte idx = 0; idx < NUM_MOTORS; idx++) {
        motors[idx].attach(motors_pins[idx]);
    }
}


/**********************************************************/
/** _motors_detach                                        */
/** Desconecta os servos de seus pinos. Isso é feito para */
/** reduzir a interferência na comunicação bluetooth, já  */
/** que o Arduino Uno possui apenas um timer e as duas    */
/** funcionalidades (servo e BT) o utilizam.              */
/** Ver: https://www.arduino.cc/en/Reference/ServoDetach  */
/**********************************************************/
void _motors_detach() {
    for (byte idx = 0; idx < NUM_MOTORS; idx++) {
        motors[idx].detach();
    }
}


/*********************************************************/
/** _blink_led                                           */
/** Emite alerta visual através do led da placa Arduino. */
/*********************************************************/
void _blink_led() {
    for (byte idx = 0; idx < 5; idx++) {
        digitalWrite(LED_BUILTIN, HIGH);
        delay(100);
        digitalWrite(LED_BUILTIN, LOW);
        delay(100);
    }
}


/*****************************************************************/
/** _put_motor_angle                                             */
/** Atribui um valor de ângulo a um servo motor.                 */
/** - parâmetro motor_idx (byte): índice do servo começando do   */
/**   valor 0 como todo array em C.                              */
/** - parâmetro angle (byte): ângulo a ser atribuído de 0 a 180. */
/** - retorno (bool): true (1) caso o ângulo seja válido.        */
/*****************************************************************/
bool _put_motor_angle(byte motor_idx, byte angle) {
    //Sucesso se o ângulo estiver dentro do intervalo [0,180]
    bool success = (angle >= 0 && angle <= 180);

    //Envia o ângulo para o servo se for válido e um valor diferente do anterior
    if (success && angle != last_angles[motor_idx]) {
        motors[motor_idx].write(angle);
        last_angles[motor_idx] = angle;
    }

    return success;
}


/*****************************************************/
/** _clear_buffer                                    */
/** Limpa o buffer de recepção da comunicação serial */
/** com o módulo bluetooth.                          */
/*****************************************************/
void _clear_buffer() {
    while (bluetooth.available()) bluetooth.read();
}


/***************************************************/
/** _request_get_all_motors_angle                  */
/** Envia por bluetooth o ângulo atual dos servos. */
/** <0xF5><ANGLE1><ANGLE2><ANGLE3><ANGLE4>         */
/***************************************************/
void _request_get_all_motors_angle() {
    //Byte com o tipo de mensagem
    bluetooth.write(0xF5);

    //Bytes com os ângulos dos servos
    for (byte idx = 0; idx < NUM_MOTORS; idx++) {
        bluetooth.write(motors[idx].read());
    }
}


/***************************************************/
/** _request_get_all_motors_angle                  */
/** Recebe por bluetooth o novo ângulo dos servos. */
/***************************************************/
void _request_put_all_motors_angle() {
    bool error = false;

    byte angles[NUM_MOTORS] = {0};

    //Tenta ler um byte para cada servo
    for (byte idx = 0; idx < NUM_MOTORS; idx++) {
        if (bluetooth.available()) {
            angles[idx] = bluetooth.read();
        } else {
            error = true;
            break;
        }
    }

    if (!error) {
        //Reconfigura os servos depois da comunicação
        _motors_attach();
        for (byte idx = 0; idx < NUM_MOTORS, !error; idx++) {
            error = !_put_motor_angle(idx, angles[idx]) || error;
        }
    }

    if (error) {
        /*Se não recebeu todos os bytes ou se algum ângulo era incorreto,
          emite alerta visual e limpa o buffer*/
        _blink_led();
        _clear_buffer();
    } else {
        //Se deu tudo certo, envia byte de confirmação
        bluetooth.write(0xF7);
    }
}


/**************/
/*GLOBAL FUNCS*/
/**************/


/*******************************************************/
/** setup                                              */
/** Função padrão do Arduino chamada uma vez ao ligar. */
/** Local ideal para configurar nossos dispositivos.   */
/*******************************************************/
void setup() {
    //Configura o led interno para saída
    pinMode(LED_BUILTIN, OUTPUT);

    //Configura a conexão serial do bluetooth
    bluetooth.begin(9600);
    delay(5000); //Tempo para o módulo acordar

    //Configura os servos e ajusta o ângulo inicial para 90
    _motors_attach();
    for (byte idx = 0; idx < NUM_MOTORS; idx++) {
        _put_motor_angle(idx, 90);
    }
}


/****************************************************/
/** loop                                            */
/** Função padrão do Arduino chamada repetidamente. */
/** Local para implementar a lógica do programa.    */
/****************************************************/
void loop() {
    if (bluetooth.available()) {
        //Chegou alguma coisa pelo bluetooth!
        byte messageType = bluetooth.read();

        /*<MESSAGE TYPE>
         *<0xF5> = Android quer saber o ângulo de cada motor
         *<0xF7><ANGLE1><ANGLE2><ANGLE3><ANGLE4> = Android quer alterar o ângulo de cada motor*/

         //Desconfigura os servos para tentar preservar a comunicação
         _motors_detach();

         switch (messageType) {
             case 0xF5: _request_get_all_motors_angle(); break;
             case 0xF7: _request_put_all_motors_angle(); break;
             default: _clear_buffer(); //Mensagem não entendida. Limpar o buffer é a solução!
         }

         //Reconfigura os servos depois da comunicação
         _motors_attach();
     }

     //Aguarda 150ms e roda o loop novamente
     delay(150);
}
