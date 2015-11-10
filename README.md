![dora](https://raw.githubusercontent.com/fractalLabs/valida-dora/master/resources/validadora.png)
Herramienta para correr una serie de procesos y obtener en JSON el resultado de todos.

## Uso

Primero que nada necesitas [lein](http://leiningen.org)

Clona el repo y entra al folder

Y para ejecutar un archivo o directorio haz:

`lein run [ARCHIVO]`

Por ejemplo prueba:

`lein run project.clj`

Las validaciones que ejecuta se encuentran en `src/valida-dora/core.clj` adentro de `(def metas [])`

```clojure
(def metas
  ["head -n 1"
   "file"
   "wc -l"])
```

Cada elemento o validación puede ser una string, que representa algo que corre en el shell y lleva como último argumento el nombre de un archivo.
O una función en Clojure que lleva como argumento un texto (el archivo representado como una string).

## Licencia
[Libre Uso MX](http://datos.gob.mx/libreusomx)
