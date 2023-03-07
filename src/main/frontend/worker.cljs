(ns frontend.worker
  (:require [cognitect.transit :as t]))

(defonce twriter (t/writer :json))

(defonce treader (t/reader :json))

(defn mock-data [] {:abc 123})


(defn init []
  (js/self.addEventListener "message"
    (fn [^js e]
      (let [request-data (.. e -data)]
        (js/postMessage request-data)))))
