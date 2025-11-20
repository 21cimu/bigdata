/**
 * 
 * flatNotify.js v0.1
 * @screenshake
 *
 * Inspired by :
 * http://tympanus.net/codrops/2014/07/23/notification-styles-inspiration/
 * 
 * Animation courtesy :
 * bounce.js - http://bouncejs.com/
 * 
 * Class manipulation
 * classie.js https://github.com/desandro/classie
 * 
 */
;( function( window ) {

  var proto_methods = {
    options: {
      // wrapper will be resolved lazily on init so that body is available
      wrapper: null,
      dismissIn: 5000
    },
    init: function() {
      // resolve wrapper each time init is called, so that document.body is not null
      var wrapper = this.options.wrapper;
      // prefer an explicitly provided wrapper, else try body, else documentElement
      if (!wrapper) wrapper = (typeof document !== 'undefined' && (document.body || document.documentElement)) || null;
      // if still not available, create a temporary container and attach to documentElement if possible
      if (!wrapper) {
        try {
          wrapper = document.createElement('div');
          if (document && document.documentElement && typeof document.documentElement.appendChild === 'function') {
            document.documentElement.appendChild(wrapper);
          }
        } catch (e) {
          // give up if DOM truly not available
          return;
        }
      }
      this.options.wrapper = wrapper;

      this.ntf = document.createElement('div');
      this.ntf.className = 'f-notification';
      var strinner = '<div class="f-notification-inner"></div><div class="f-close">x</div>';
      this.ntf.innerHTML = strinner;

      // Prefer insertBefore when lastChild exists, otherwise appendChild (safer when wrapper is document.body/documentElement)
      try {
        if (typeof wrapper.insertBefore === 'function' && wrapper.lastChild) {
          wrapper.insertBefore(this.ntf, wrapper.lastChild);
        } else if (typeof wrapper.appendChild === 'function') {
          wrapper.appendChild(this.ntf);
        }
      } catch (e) {
        // last-resort append to documentElement if available
        try { (document && document.documentElement && document.documentElement.appendChild(this.ntf)); } catch (ex) { /* ignore */ }
      }

      // init events
      this.initEvents();
    },
    initEvents: function() {
      var self = this;
      // dismiss notification
      this.ntf.querySelector('.f-close').addEventListener('click', function() {
        self.dismiss();
      });
    },
    dismiss: function() {
      var self = this;
      clearTimeout(this.dismissttl);

      try {
        classie.remove(self.ntf, 'f-show');
        setTimeout(function() {
          classie.add(self.ntf, 'f-hide');
        }, 25);

        setTimeout(function() {
          try {
            var w = self.options && self.options.wrapper;
            if (w && typeof w.removeChild === 'function' && w.contains && w.contains(self.ntf)) {
              w.removeChild(self.ntf);
            } else if (self.ntf && self.ntf.parentNode && typeof self.ntf.parentNode.removeChild === 'function') {
              self.ntf.parentNode.removeChild(self.ntf);
            }
          } catch (e) { /* ignore */ }
        }, 500);
      } catch (e) {
        // ensure no exception bubbles
        try { if (self.ntf && self.ntf.parentNode) self.ntf.parentNode.removeChild(self.ntf); } catch (ex) {}
      }

    },
    setType: function(newType) {
      var self = this;

      classie.remove(self.ntf, 'f-notification-error');
      classie.remove(self.ntf, 'f-notification-alert');
      classie.remove(self.ntf, 'f-notification-success');

      classie.add(self.ntf, newType);

    },
    success: function(message, dismissIn) {
      var self = this;

      /**
       * Use supplied dismiss timeout if present, else uses default value.
       * If set to 0, doesnt automatically dismiss.
       */
      dismissIn = (typeof dismissIn === "undefined") ? this.options['dismissIn'] : dismissIn;

      /**
       * Set notification type styling
       */
      self.setType('f-notification-success');

      self.ntf.querySelector('.f-notification-inner').innerHTML = message;

      classie.remove(self.ntf, 'f-hide');
      classie.add(self.ntf, 'f-show');

      if (dismissIn > 0) {
        this.dismissttl = setTimeout(function() {
          self.dismiss();
        }, dismissIn);
      }
    },
    error: function(message, dismissIn) {
      var self = this;

      /**
       * Use supplied dismiss timeout if present, else uses default value.
       * If set to 0, doesnt automatically dismiss.
       */
      dismissIn = (typeof dismissIn === "undefined") ? this.options['dismissIn'] : dismissIn;

      /**
       * Set notification type styling
       */
      self.setType('f-notification-error');

      self.ntf.querySelector('.f-notification-inner').innerHTML = message;

      classie.remove(self.ntf, 'f-hide');
      classie.add(self.ntf, 'f-show');

      if (dismissIn > 0) {
        this.dismissttl = setTimeout(function() {
          self.dismiss();
        }, dismissIn);
      }
    },
    alert: function(message, dismissIn) {
      var self = this;

      /**
       * Use supplied dismiss timeout if present, else uses default value.
       * If set to 0, doesnt automatically dismiss.
       */
      dismissIn = (typeof dismissIn === "undefined") ? this.options['dismissIn'] : dismissIn;

      /**
       * Set notification type styling
       */
      self.setType('f-notification-alert');

      self.ntf.querySelector('.f-notification-inner').innerHTML = message;

      classie.remove(self.ntf, 'f-hide');
      classie.add(self.ntf, 'f-show');

      if (dismissIn > 0) {
        this.dismissttl = setTimeout(function() {
          self.dismiss();
        }, dismissIn);
      }
    }
  }, flatNotify, _flatNotifiy;

  _flatNotifiy = function() {
    this.init();
  };

  _flatNotifiy.prototype = proto_methods;

  flatNotify = function() {
    return new _flatNotifiy();
  };

  /**
   * add to global namespace
   */
  window.flatNotify = flatNotify;

} )( window );

/*==========*/
( function( window ) {

'use strict';

// class helper functions from bonzo https://github.com/ded/bonzo

function classReg( className ) {
  return new RegExp("(^|\\s+)" + className + "(\\s+|$)");
}

// classList support for class management
// altho to be fair, the api sucks because it won't accept multiple classes at once
var hasClass, addClass, removeClass;

if ( 'classList' in document.documentElement ) {
  hasClass = function( elem, c ) {
    return elem.classList.contains( c );
  };
  addClass = function( elem, c ) {
    elem.classList.add( c );
  };
  removeClass = function( elem, c ) {
    elem.classList.remove( c );
  };
}
else {
  hasClass = function( elem, c ) {
    return classReg( c ).test( elem.className );
  };
  addClass = function( elem, c ) {
    if ( !hasClass( elem, c ) ) {
      elem.className = elem.className + ' ' + c;
    }
  };
  removeClass = function( elem, c ) {
    elem.className = elem.className.replace( classReg( c ), ' ' );
  };
}

function toggleClass( elem, c ) {
  var fn = hasClass( elem, c ) ? removeClass : addClass;
  fn( elem, c );
}

var classie = {
  // full names
  hasClass: hasClass,
  addClass: addClass,
  removeClass: removeClass,
  toggleClass: toggleClass,
  // short names
  has: hasClass,
  add: addClass,
  remove: removeClass,
  toggle: toggleClass
};

// transport
if ( typeof define === 'function' && define.amd ) {
  // AMD
  define( classie );
} else {
  // browser global
  window.classie = classie;
}

})( window );
