(ns frontend.worker
  (:require [cognitect.transit :as t]))

(defn mock-data [] {:abc 123})

(defmulti handle-message :type)

(defmethod handle-message :init
  [data]
  (assoc data :response "Setting up worker, e.g. csrf-token."))

(defmethod handle-message :validate
  [data]
  (assoc data :response "Maybe I run a validation for you?"))

(defmethod handle-message :default
  [data]
  (assoc data :response "I don't have a way to handle this kind of request"))

(defn init []
  (let [twriter (t/writer :json)
        treader (t/reader :json)]
  (js/self.addEventListener "message"
    (fn [^js e]
      (let [transit-request-data (.. e -data)
            request-data (t/read treader transit-request-data)
            response-data (handle-message request-data)
            transit-response-data (t/write twriter response-data)]
        (js/postMessage transit-response-data))))))

(comment
  )
