(ns crx77.core
    (:require [clojure.java.io :as io])
    (:require [clojure.pprint :as pprint])
    (:require [clojure.string :as str])
    (:require [clojure.set :as sets])
    (:gen-class))
  
  (def SOURCE_SEPARATOR #"<=")
  (def SOURCE_ID_REGEXP #"^[\w]+$")
  (def DEP_SEPARATOR #",")
  (def ALT_DEP_SEPARATOR #"\|")
  (def CALCULATED "calculated")
  
  (def inputFile "resources/input.txt")
  
  (defn p [i]
    (pprint/pprint i)
  )
  
  (defn pl [m,i]
    (println (format "%s %s" m i))
  )
  
  (defn validateRuleId [inp]
    (re-matches SOURCE_ID_REGEXP (str/trim inp))
  )
  
  (defn checkDep [rule]
    (let [
      shadows (str/split rule ALT_DEP_SEPARATOR)
      trimmed (str/trim rule)
      ]
      (if (> (count shadows) 1)
        (filterv some? (mapv checkDep shadows))
        (if-not (nil? (validateRuleId rule))
          trimmed
        )
      )  
    )
  )
  
  (defn validateDeps [inp]
    (let [
      parts (str/split inp DEP_SEPARATOR)
      trimmed (str/trim inp)
    ]
      (if (> (count trimmed) 0)
        (filterv some? (mapv checkDep parts))
        CALCULATED
      )
    )
  )
  
  (defn str2Kwd [v]
    (mapv keyword v)
  )
  
  (defn createGlobalRule [id,deps, processed]
    (hash-map :crux.db/id (keyword id)
      :depends/on (str2Kwd deps)
      :is/goal true
      :is/async true
      :is/valid true
      :is/processed processed
      :shadow/of :none) 
  )
  
  (defn getShadowIdKwd [parent, id]
    (keyword (str "__" parent "_" id ))
  )
  
  (defn createShadow [deps, parent, cruxId]
    (hash-map :crux.db/id cruxId
      :depends/on (vector (keyword deps))
      :shadow/of (keyword parent)
      :is/goal false
      :is/async true
      :is/valid true
      :is/processed false
    )
  )
  
  (defn createSimpleTask [id]
    (hash-map 
      :crux.db/id (keyword id)
      :is/goal false
      :shadow/of :none
      :is/processed false
    )
  )
  
  (defn createDepsRules [deps,parent]
    (mapv 
      (fn [r] 
        (if (vector? r)
          (let [
                p1CruxId (getShadowIdKwd parent 1)
                p1 (createShadow (first r) parent p1CruxId)]
            (into 
              (vector p1) 
              (map-indexed 
                (fn [idx,itm] 
                  (createShadow itm p1CruxId 
                    (getShadowIdKwd parent (+ idx 2))))
                (rest r)
              )
            )
          )
          r
        )
      ) 
    deps)
  )
  
  (defn depRulesToIds [rules]
    (mapv 
      (fn [r] 
        (if (vector? r)
          (name (get (first r) :crux.db/id))
          r
        )
      ) 
    rules)
  )
  
  (defn splitSource [s]
    (let [parts (str/split s SOURCE_SEPARATOR)]
      (let [rule (str/trim (nth parts 0 ""))]
        (if-not (nil? (validateRuleId rule))
          (do 
            (let [d (validateDeps (nth parts 1 ""))]
              (if (vector? d)
                (if-not (= (count d) 0)
                  (let [depsVec (createDepsRules d rule)]
                    (conj depsVec (createGlobalRule rule (depRulesToIds depsVec) false))
                  )
                  (pl "rule deps are invalid " rule)
                )
                (vector (createGlobalRule rule (vector) true))
              )
            )
          )
          (pl "rule id is invalid " rule)
        )
      )
    )
  )
  
  (defn getStringDeps [rSet]
    (mapv (fn [item] 
      (if (string? item)
        item
        (if (vector? item)
          (mapv (fn [i] (name (first (get i :depends/on)))) item)
        )
      ))
    rSet)
  )
  
  (defn getGoals [rSet]
    (mapv (fn [item] (if (map? item) (name (get item :crux.db/id))) ) rSet)
  )
  
  (defn getReadyToUse [rSet]
    (filter some? (mapv 
      (fn [i] 
        (if (map? i)
          i
          (if (vector? i)
            (getReadyToUse i)
          )
        )
      )
    rSet))
  )
  
  (defn prepareForDB [rSet]
    (let [ 
      simpleDeps (distinct (flatten (mapv  getStringDeps rSet)))
      goals (distinct (flatten (mapv  getGoals rSet))) 
      readyToUse (flatten (getReadyToUse rSet))]
      (into 
          (mapv createSimpleTask (sets/difference (set simpleDeps) (set goals)))
          (vec readyToUse)
      )
    )
  )