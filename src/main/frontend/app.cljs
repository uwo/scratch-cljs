(ns frontend.app
  (:require [cljs.core.async :as a]
            ))

(def submit-chan (a/chan))

(defn start-loop
  [submit-chan]
  (a/go
    (loop []
      (when-let [submission (a/<! submit-chan)]
        (prn "received" submission)
        (recur)))))

(defn init []
  (println "Hello World"))

(comment
  (prn "test")

  (start-loop submit-chan)
  (a/put! submit-chan {:test 'asdf})
  )
