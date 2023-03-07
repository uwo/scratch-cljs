(ns frontend.app
  ;; Used https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers
  (:require [cljs.core.async :as a]
            [cognitect.transit :as t]))

(def req-chan (a/chan))

(def res-chan (a/chan))

(defonce twriter (t/writer :json))
(defonce treader (t/reader :json))

(defn post-message
  [worker message]
  (.postMessage worker (t/write twriter message)))

(defn start-queuing-loop
  [worker req-chan res-chan]
  (prn "Starting queuing loop")
  (a/go
    (loop []
      (when-let [req (a/<! req-chan)]
        (post-message worker req)
        (when-let [res (a/<! res-chan)]
          ;; handle response
          (prn "handling response" res)
          (recur))))))

(defn request-job
  ;; Queue the request to enforce order, as opposed to calling
  ;; post-message on the worker directly.
  [req-chan job]
  (a/put! req-chan job))

(defn create-worker
  [{:keys [worker-file res-chan req-chan]}]
  (let [worker (js/Worker. worker-file)]
    (.addEventListener worker "message" (fn [e] (a/put! res-chan (t/read treader (.. e -data)))))
    (request-job req-chan {:type :init :data "some init data"})
    worker))

(defn init []
  (println "Hello World")
  ;; Uncomment to test on refresh testing
  #_(let [worker (create-worker {:worker-file "/js/worker.js"
                                 :req-chan req-chan
                                 :res-chan res-chan})]
      (start-queuing-loop worker req-chan res-chan)
    (request-job req-chan {:type :validate :data "hello world"})))

(comment
  (def worker
    (create-worker {:worker-file "/js/worker.js"
                    :req-chan req-chan
                    :res-chan res-chan}))

  (start-queuing-loop worker req-chan res-chan)

  (request-job req-chan {:type :validate :data "hello world"})

  ;; Note that response handlers are in order
  (dotimes [n 10]
    (request-job req-chan {:type :validate :data "hello world" :n n}))

  )
