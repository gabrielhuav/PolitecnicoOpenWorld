# SPEC · Metamorfosis de La Presidenta (2026-07-21)

> Dictado por el dueño. **Aún NO implementado**: los cuadros están recortados y ordenados,
> pero los disparadores y la elección aleatoria son trabajo de motor.
> Poses en `tools/_para_corregir/metamorfosis_*/`, ya renumeradas en orden de animación.

## A · Metamorfosis LARGA — Yoalli ➜ La Presidenta (arcade)

**Carpeta:** `metamorfosis_yoalli_a_presidenta/` · **9 poses** · `_01` … `_09`

**Cuándo:** al **iniciar la última pelea del arcade**, después de derrotar a Yoalli Ehécatl.
Es la transformación de vuelta: Yoalli se convierte en La Presidenta para el combate final.

**Orden:** los 9 cuadros **en secuencia, del `_01` al `_09`**, sin aleatoriedad.
`_01` = Yoalli completa → `_09` = La Presidenta.

## B · Metamorfosis CORTA — ➜ La Presidenta (selección fuera de arcade)

**Carpeta:** `metamorfosis_HACIA_presidenta_seleccion/` · **6 poses**

**Cuándo:** al seleccionar a La Presidenta **fuera del modo arcade**.

**Orden:** **3 pasos**, con el primero ALEATORIO:

```
random(_01, _02, _03, _04)  ->  _05  ->  _06
```

| Pose | Qué es |
|---|---|
| `_01` | Yoalli completa |
| `_02` | Aura morada, ojos rojos |
| `_03` | Forma de sombra |
| `_04` | Forma de fuego |
| `_05` | La Presidenta con los ojos cerrados (**siempre**) |
| `_06` | **La Presidenta normal — último cuadro siempre.** Es el que casa con el resto de sus assets |

La aleatoriedad del primer cuadro es solo variedad visual: el destino es siempre el mismo.

## ⚠️ Ojo al implementar

1. **No confundir con la metamorfosis que YA existe.** Hoy el motor tiene
   `BONUS_POWER_11` + `completePresidentaMetamorphosis`: La Presidenta ➜ Yoalli a mitad de
   combate (50 % HP). Las dos de este documento van en **sentido contrario** y se disparan
   al EMPEZAR, no durante.
2. **Los recortes conservan el rótulo "STEP N"** del generador en la banda superior. El
   importador (`tools/sf_import_fixed_pose.py`) los borra al meterlos, pero si se procesan
   por otra vía hay que quitarlos.
3. Los rótulos originales venían **salteados y sin relación con el orden real**
   (1, 3, 4, 9, 5…). **Manda la numeración del nombre de archivo**, no el rótulo pintado.
