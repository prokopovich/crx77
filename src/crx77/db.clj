(ns crx77.core
    (:gen-class)
    (:require [clojure.pprint :as pprint])
    (:require [clojure.string :as str])
    )
    (require '[crux.api :as crux])
    (import (crux.api ICruxAPI))
    

  (def ^crux.api.ICruxAPI node
    (crux/start-node {:crux.node/topology :crux.standalone/topology
                      :crux.node/kv-store "crux.kv.memdb/kv"
                      :crux.kv/db-dir "data/db-dir-1"
                      :crux.standalone/event-log-dir "data/eventlog-1"
                      :crux.standalone/event-log-sync? true
                      :crux.standalone/event-log-kv-store "crux.kv.memdb/kv"}))
  
(defn p [i]
    (pprint/pprint i)
)

(defn pl [m,i]
    (println (format "%s %s" m i))
)
(defn p [i]
  (pprint/pprint i)
)

(defn pl [m,i]
  (println (format "%s %s" m i))
)
  (defn _entity [id]
    (crux/entity (crux/db node) id)
  )
  
  (defn _q [i]
    (crux/q (crux/db node) i)
  )
  
  (defn getRelations [id]
    (_q {
      :find '[p2]
      :where '[
          [p2 :is/valid true]
          [p2 :depends/on d]
          [p2 :is/processed false]
          ]
      :args [{'d id}]
      }
    )
  )
  
  (defn updateEntity [e]
    (crux/await-tx node
      (crux/submit-tx node [[:crux.tx/put e]])
    )
  )
  
  (defn deleteEntity [e]
    (crux/await-tx node
      (crux/submit-tx node [[:crux.tx/evict e]])
    )
  )
  
  (defn markRuleInvalid [rule]
    (let [r (_entity rule)]
      (if-not (= (get r :is/valid) false)
        (updateEntity (assoc-in r [:is/valid] false))
      )
    )
  )
  
  (defn markRuleValid [rule]
    (let [r (_entity (first rule))]
      (updateEntity (assoc-in r [:is/valid] true))
    )
  )
  (declare checkPath)
  (defn checkPathShadow [edge,acc]
    (let [r (_entity  edge)
          edges (into (vector edge) acc)]
      (mapv (fn [n] (checkPath n edges)) (get r :depends/on))    
    )
  )
  
  (defn checkPath [edge,acc]
    (let [
          r (_entity edge)
          edges (if (true? (get r :is/goal)) (conj acc edge) acc)
          ]
      (if-not (apply distinct? edges)
        ;TODO - implement break mechanism to prevent further processing
        (markRuleInvalid (first edges))
        (do
          (if-not (nil? (get r :depends/on))
            (if-not (= (get r :shadow/of) :none)
              (checkPathShadow edge acc)
              (mapv (fn [n] (checkPath n (conj acc edge))) (get r :depends/on))
            )
          )
          (if-not (= (get r :shadow/of) :none)
            (mapv (fn [n] (checkPathShadow (first n) acc)) (_q {
              :find '[p2]
              :where '[[p2 :shadow/of d]]
              :args [{'d (get r :crux.db/id)}]
              })
            )
          )    
        )
      )  
    )
  )
  
  (defn checkRulesReachability []
    (mapv (fn [n] (checkPath (first n) (vector))) (_q '{
      :find [p1]
      :where [
          [p1 :is/goal true]
          [p1 :is/processed false]
          ]})
    )
  )
  
  (defn markRuleAsNonAsync [r]
    (if-not (false? (get r :is/async))
      (updateEntity (assoc-in r [:is/async] false))
    )
  )
  
  (defn checkForMultitasking [id,threshold]
    (let [rList (getRelations (first id))]
      (if (> (count rList) threshold)
        (mapv (fn [n] 
            (let [r (_entity (first n))]
              (markRuleAsNonAsync r)
              (if-not (= (get r :shadow/of) :none)
                (markRuleAsNonAsync (_entity (get r :shadow/of)))
              )
            )
          )
          rList
        )
      )
    )
  )
  
  (defn processShadows [sList]
    (let [
      primary (_entity (first sList))
      secondary (_entity (last sList))]
      ;primary could be at the top of the hierarcy, we should not touch it
      (if-not (= (get primary :shadow/of) :none)
        (when (false? (get primary :is/valid))
          ;primary is not valid. swap it with secondary
          (p "swaping shadows")
          (pl (get primary :crux.db/id) (get secondary :crux.db/id))
          (updateEntity (assoc-in (assoc-in secondary [:crux.db/id] (get primary :crux.db/id)) [:shadow/of] (get primary :shadow/of)))
        ;we can improve performance by checking rule deps here
        ;so we can choose alternative with less cost
        ;but lets do it later
        )
      )
    )
  )
  
  (defn checkValidAfterShadows [rList]
    (let [
      primaryId (first rList)
      secondaryId (last rList)
      secondary (_entity (last rList))]
      (if (false? (get secondary :is/valid))
        (markRuleInvalid primaryId)
      )
      (mapv (fn [n] (deleteEntity (first n))) (_q {
        :find '[p]
        :where '[[p :shadow/of d]]
        :args [{'d secondaryId}]
        }
      ))
    )
  )
  
  (defn collectAsyncRuleDeps [edge,spacer]
    (let [
          r (_entity edge)]
        (if-not (str/starts-with? (name edge) "__")
          (print edge spacer)
        )
        (if-not (true? (get r :is/processed))
          (updateEntity (assoc-in r [:is/processed] true))
        )
        (if-not (nil? (get r :depends/on))
          (do
            (mapv (fn [n] (collectAsyncRuleDeps n " ")) (get r :depends/on))
          )
        )
    )
  )
  
  (defn processAsyncRule [rid]
    (collectAsyncRuleDeps (first rid) "-> ")
    (println "")
  )
  
  (defn processNonAsyncRuleDep [rid, spacer]
    (let [
      r (_entity rid)]
      (if-not (true? (get r :is/processed))
        (do
          (if-not (str/starts-with? (name rid) "__")
            (print rid spacer)
            (mapv (fn [n] (processNonAsyncRuleDep n " ")) (get r :depends/on))
          )      
          (updateEntity (assoc-in r [:is/processed] true))
        )
      )
    )
  )
  (defn collectGoalsInDeps [edge]
    (let [
          r (_entity edge)
          deps (_q {:find '[p2] 
          :where '[
            [p1 :crux.db/id rid]
            [p1 :depends/on p2]
            [p2 :is/valid true]
            [p2 :is/goal true]
            [p2 :is/processed false]
          ]
          :args [{'rid edge}]
          })]
      (if-not (true? (get r :is/processed))
        (do
          (mapv (fn [n] (collectGoalsInDeps (first n))) deps)
          (processNonAsyncRuleDep edge " -> ")
          (mapv (fn [n] (processNonAsyncRuleDep n " ")) (get r :depends/on))
          (println)    
        )
      )
    )
  )
  
  (defn processNonAsyncRule [rid]
    (collectGoalsInDeps (first rid))
  )
  
(defn startDBStuff[tasks]

    (def tx (crux/submit-tx
        node
        (mapv (fn [n] [:crux.tx/put n]) tasks)
    ))
        
    (crux/await-tx node tx)
    
    ;check rules for availability
    (checkRulesReachability)
    ;decide which way to calculate for rules with 2+ options
    (mapv processShadows (_q '{
        :find [p1 p2]
        :where [
        [p2 :shadow/of p1]
        [p2 :is/valid true]
        [(not= p1 :none)]
        ]
        }
    ))
    ;check if rule valid
    (mapv checkValidAfterShadows (_q '{
        :find [p1 p2]
        :where [
        [p2 :shadow/of ?root]
        [p1 :shadow/of :none]
        [p1 :crux.db/id ?root]
        [p1 :is/valid true]
        ]
        }
    ))
    ;previous steps could change the world
    ;check rules for availability again
    (mapv markRuleValid (_q '{:find [p1]
        :where [
        [p1 :is/goal true]
        [p1 :is/valid false]
        ]}))    
    (checkRulesReachability)
    
    (p "invalid rules:")
    (p (_q '{:find [p1]
        :where [
        [p1 :is/goal true]
        [p1 :is/valid false]
        ]}))

    ;check for async based on non-goal parts
    (mapv #(checkForMultitasking % 1) (_q '{:find [p] 
        :where[
        [p :is/goal false]
        ]})
    )
    ;check for async based on goal parts
    (mapv #(checkForMultitasking % 0) (_q '{:find [p] 
        :where[
        [p :is/goal true]
        [p :is/processed false]
        [p :shadow/of :none]
        ]})
    )    
    ;show paralel rules
    (p "ASYNC RUN:")
    (mapv processAsyncRule (_q {:find '[p] 
        :where '[
        [p :is/goal true]
        [p :is/async true]
        [p :is/valid true]
        [p :shadow/of :none]  
        ]})
    )
    (p "-----------")
    (mapv processNonAsyncRule (_q {:find '[p] 
        :where '[
        [p :is/goal true]
        [p :is/async false]
        [p :is/valid true]
        [p :shadow/of :none]
        ]})
    )

    (.close node)
    (println "DB killed...")
)  