(ns ^:figwheel-hooks square-draw.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defonce state (r/atom {
  :tool :draw
  :color "#bada55"
  :file []
  :selected 0}))



(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "2d"))

(defn render! []
  (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))
  (dorun
    (for [el (:file @state)]
      (do 
        (set! (.-fillStyle ctx) (:fill el "silver"))
        (.fillRect ctx (:x el) (:y el) (:w el) (:h el)))))
  (when-let [el (get (:file @state) (:selected @state))]
    (.setLineDash ctx #js [6])
    (.strokeRect ctx (:x el) (:y el) (:w el) (:h el))))

; ideally this would schedule render! in a requestAnimationFrame
(add-watch state :render
  (fn [k r o n]
    (when (or (not= (:file o) (:file n))
              (not= (:selected o) (:selected n)))
      (render!))))



(defn mouse->canvas [x y]
  ; hardcoded in the css
  [(- x 100) (- y 12)])

(defn pick [cx cy]
  ; return a vector of every element overlapping the given coords
  (let [[cx cy] (mouse->canvas cx cy)]
    (filterv 
      (fn [[idx {:keys [x y w h]}]]
        (and (< x cx (+ x w))
             (< y cy (+ y h))))
      (reverse (map-indexed vector (:file @state))))))

(defn on-draw [e]
  ; note the initial mouse x/y and create a new element in the file vector
  ; then set up a mouse move fn that modifies this new element's width based on the current mouse position
  (let [[x y] (mouse->canvas (.-x e) (.-y e))
        idx (count (:file @state))]
    (swap! state update :file conj {:x x :y y :w 0 :h 0 :fill (:color @state)})
    (swap! state assoc :selected idx)
    (swap! state assoc :movefn 
      (fn [e]
        (let [[mx my] (mouse->canvas (.-x e) (.-y e))
              el (get-in @state [:file idx])]
          (swap! state update-in [:file idx] assoc :w (- mx x) :h (- my y)))
        ))
    ))

(defn on-move [e]
  (let [[x y] [(.-x e) (.-y e)]]
    (if-let [[idx picked] (first (pick (.-x e) (.-y e)))]
      (do 
        (swap! state assoc :selected idx)
        (swap! state assoc :movefn 
          ; calculate the delta mouse x/y from the start of the move then add that to the inital
          ; picked element's position
          (fn [e]
            (let [[nx ny] [(.-x e) (.-y e)]]
              (swap! state update-in [:file idx] assoc 
                :x (+ (:x picked) (- nx x)) 
                :y (+ (:y picked) (- ny y)))))))
      (swap! state assoc :selected nil)
      )))

; Input cycles, dispatch on mouse down based on the current tool.  If move/up handlers are needed
; they can be set up in the dispatched fn

(defn pointer-down [e]
  ;(js/console.log e)
  (when (= (.-target e) canvas)
    (cond
      (= (:tool @state) :draw) (on-draw e)
      (= (:tool @state) :move) (on-move e)
      )))

(defn pointer-move [e]
  (if (:movefn @state) ((:movefn @state) e)))

(defn pointer-up [e]
  ;(js/console.log e)
  (if (:upfn @state) ((:upfn @state) e))
  (swap! state dissoc :movefn :upfn))

(js/addEventListener "pointerdown" pointer-down)
(js/addEventListener "pointermove" pointer-move)
(js/addEventListener "pointerup"   pointer-up)


; UI

(defn toolbar-ui []
  [:div.toolbar
    (into [:div]
      (map 
        (fn [k] 
          [:div.tool 
            {:class (if (= (:tool @state) k) "active")
             :on-click (fn [e] 
              (swap! state assoc :tool k)
              (js/document.body.setAttribute "class" (name k)))}
            k])
        [:move :draw]))
    [:div "fillcolor"]
    [:input {
      :type :color 
      :value (:color @state)
      :on-change (fn [e] (swap! state assoc :color (.. e -target -value)))}]
    (when-let [idx (:selected @state)]
      [:button {
        :on-click (fn [e] 
          (swap! state assoc 
            :file (vec (concat 
                    (subvec (:file @state) 0 idx)
                    (subvec (:file @state) (inc idx))))
            :selected nil))} 
       "delete"])])

(defn root-ui []
  [:div
    [toolbar-ui]])

(rd/render [root-ui] (js/document.querySelector "#app"))

(render!)