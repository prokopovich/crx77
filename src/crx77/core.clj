(ns crx77.core
  (:require [clojure.java.io :as io])
  (:require [clojure.pprint :as pprint])
  (:require [clojure.string :as str])
  (:require [clojure.set :as sets])
  (:gen-class))
(load "parse")
(load "db")


(defn -main [] 
    (println (str "CRX 77  started"))
    (if (.exists (io/file inputFile))
      (do
        (with-open [rdr (clojure.java.io/reader inputFile)]
          (startDBStuff (prepareForDB (doall (map splitSource (line-seq rdr)))))
        )
      )
    )
)