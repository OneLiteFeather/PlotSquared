package com.intellectualcrafters.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * A JSONObject is an unordered collection of name/value pairs. Its external form is a string wrapped in curly braces
 * with colons between the names and values, and commas between the values and names. The internal form is an object
 * having <code>get</code> and <code>opt</code> methods for accessing the values by name, and <code>put</code> methods
 * for adding or replacing values by name. The values can be any of these types: <code>Boolean</code>,
 * <code>JSONArray</code>, <code>JSONObject</code>, <code>Number</code>, <code>String</code>, or the
 * <code>JSONObject.NULL</code> object. A JSONObject constructor can be used to convert an external form JSON text into
 * an internal form whose values can be retrieved with the <code>get</code> and <code>opt</code> methods, or to convert
 * values into a JSON text using the <code>put</code> and <code>toString</code> methods. A <code>get</code> method
 * returns a value if one can be found, and throws an exception if one cannot be found. An <code>opt</code> method
 * returns a default value instead of throwing an exception, and so is useful for obtaining optional values.
 *
 * The generic <code>get()</code> and <code>opt()</code> methods return an object, which you can cast or query for type.
 * There are also typed <code>get</code> and <code>opt</code> methods that do type checking and type coercion for you.
 * The opt methods differ from the get methods in that they do not throw. Instead, they return a specified value, such
 * as null.
 *
 * The <code>put</code> methods add or replace values in an object. For example,
 *
 *
 * <pre>
 * myString = new JSONObject().put(&quot;JSON&quot;, &quot;Hello, World!&quot;).toString();
 * </pre>
 *
 * produces the string <code>{"JSON": "Hello, World"}</code>.
 *
 * The texts produced by the <code>toString</code> methods strictly conform to the JSON syntax rules. The constructors
 * are more forgiving in the texts they will accept: <ul> <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may
 * appear just before the closing brace.</li> <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li> <li>Strings do not need to be quoted at all if they do not begin with a quote or single quote,
 * and if they do not contain leading or trailing spaces, and if they do not contain any of these characters: <code>{ }
 * [ ] / \ : , #</code> and if they do not look like numbers and if they are not the reserved words <code>true</code>,
 * <code>false</code>, or <code>null</code>.</li> </ul>
 *
 * @author JSON.org
 * @version 2014-05-03
 */
public class JSONObject {
    /**
     * It is sometimes more convenient and less ambiguous to have a <code>NULL</code> object than to use Java's
     * <code>null</code> value. <code>JSONObject.NULL.equals(null)</code> returns <code>true</code>.
     * <code>JSONObject.NULL.toString()</code> returns <code>"null"</code>.
     */
    public static final Object NULL = new Null();
    /**
     * The map where the JSONObject's properties are kept.
     */
    private final Map<String, Object> map;
    
    /**
     * Construct an empty JSONObject.
     */
    public JSONObject() {
        map = new HashMap<String, Object>();
    }
    
    /**
     * Construct a JSONObject from a subset of another JSONObject. An array of strings is used to identify the keys that
     * should be copied. Missing keys are ignored.
     *
     * @param jo    A JSONObject.
     * @param names An array of strings.
     *
     * @throws JSONException
     * @throws JSONException If a value is a non-finite number or if a name is duplicated.
     */
    public JSONObject(final JSONObject jo, final String[] names) {
        this();
        for (final String name : names) {
            try {
                putOnce(name, jo.opt(name));
            } catch (JSONException ignore) {
            }
        }
    }
    
    /**
     * Construct a JSONObject from a JSONTokener.
     *
     * @param x A JSONTokener object containing the source string.
     *
     * @throws JSONException If there is a syntax error in the source string or a duplicated key.
     */
    public JSONObject(final JSONTokener x) throws JSONException {
        this();
        char c;
        String key;
        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
                case 0:
                    throw x.syntaxError("A JSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();
            }
            // The key is followed by ':'.
            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            putOnce(key, x.nextValue());
            // Pairs are separated by ','.
            switch (x.nextClean()) {
                case ';':
                case ',':
                    if (x.nextClean() == '}') {
                        return;
                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }
    
    /**
     * Construct a JSONObject from a Map.
     *
     * @param map A map object that can be used to initialize the contents of the JSONObject.
     *
     * @throws JSONException
     */
    public JSONObject(final Map<String, Object> map) {
        this.map = new HashMap<String, Object>();
        if (map != null) {
            for (final Entry<String, Object> entry : map.entrySet()) {
                final Object value = entry.getValue();
                if (value != null) {
                    this.map.put(entry.getKey(), wrap(value));
                }
            }
        }
    }
    
    /**
     * Construct a JSONObject from an Object using bean getters. It reflects on all of the public methods of the object.
     * For each of the methods with no parameters and a name starting with <code>"get"</code> or <code>"is"</code>
     * followed by an uppercase letter, the method is invoked, and a key and the value returned from the getter method
     * are put into the new JSONObject.
     *
     * The key is formed by removing the <code>"get"</code> or <code>"is"</code> prefix. If the second remaining
     * character is not upper case, then the first character is converted to lower case.
     *
     * For example, if an object has a method named <code>"getName"</code>, and if the result of calling
     * <code>object.getName()</code> is <code>"Larry Fine"</code>, then the JSONObject will contain <code>"name": "Larry
     * Fine"</code>.
     *
     * @param bean An object that has getter methods that should be used to make a JSONObject.
     */
    public JSONObject(final Object bean) {
        this();
        populateMap(bean);
    }
    
    /**
     * Construct a JSONObject from an Object, using reflection to find the public members. The resulting JSONObject's
     * keys will be the strings from the names array, and the values will be the field values associated with those keys
     * in the object. If a key is not found or not visible, then it will not be copied into the new JSONObject.
     *
     * @param object An object that has fields that should be used to make a JSONObject.
     * @param names  An array of strings, the names of the fields to be obtained from the object.
     */
    public JSONObject(final Object object, final String names[]) {
        this();
        final Class c = object.getClass();
        for (final String name : names) {
            try {
                putOpt(name, c.getField(name).get(object));
            } catch (JSONException | SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException ignore) {
            }
        }
    }
    
    /**
     * Construct a JSONObject from a source JSON text string. This is the most commonly used JSONObject constructor.
     *
     * @param source A string beginning with <code>{</code>&nbsp;<small>(left brace)</small> and ending with
     *               <code>}</code> &nbsp;<small>(right brace)</small>.
     *
     * @throws JSONException If there is a syntax error in the source string or a duplicated key.
     */
    public JSONObject(final String source) throws JSONException {
        this(new JSONTokener(source));
    }
    
    /**
     * Construct a JSONObject from a ResourceBundle.
     *
     * @param baseName The ResourceBundle base name.
     * @param locale   The Locale to load the ResourceBundle for.
     *
     * @throws JSONException If any JSONExceptions are detected.
     */
    public JSONObject(final String baseName, final Locale locale) throws JSONException {
        this();
        final ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale, Thread.currentThread().getContextClassLoader());
        // Iterate through the keys in the bundle.
        final Enumeration<String> keys = bundle.getKeys();
        while (keys.hasMoreElements()) {
            final Object key = keys.nextElement();
            if (key != null) {
                // Go through the path, ensuring that there is a nested
                // JSONObject for each
                // segment except the last. Add the value using the last
                // segment's name into
                // the deepest nested JSONObject.
                final String[] path = ((String) key).split("\\.");
                final int last = path.length - 1;
                JSONObject target = this;
                for (int i = 0; i < last; i += 1) {
                    final String segment = path[i];
                    JSONObject nextTarget = target.optJSONObject(segment);
                    if (nextTarget == null) {
                        nextTarget = new JSONObject();
                        target.put(segment, nextTarget);
                    }
                    target = nextTarget;
                }
                target.put(path[last], bundle.getString((String) key));
            }
        }
    }
    
    /**
     * Produce a string from a double. The string "null" will be returned if the number is not finite.
     *
     * @param d A double.
     *
     * @return A String.
     */
    public static String doubleToString(final double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return "null";
        }
        // Shave off trailing zeros and decimal point, if possible.
        String string = Double.toString(d);
        if ((string.indexOf('.') > 0) && (string.indexOf('e') < 0) && (string.indexOf('E') < 0)) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }
    
    /**
     * Get an array of field names from a JSONObject.
     *
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(final JSONObject jo) {
        final int length = jo.length();
        if (length == 0) {
            return null;
        }
        final Iterator<String> iterator = jo.keys();
        final String[] names = new String[length];
        int i = 0;
        while (iterator.hasNext()) {
            names[i] = iterator.next();
            i += 1;
        }
        return names;
    }
    
    /**
     * Get an array of field names from an Object.
     *
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(final Object object) {
        if (object == null) {
            return null;
        }
        final Class klass = object.getClass();
        final Field[] fields = klass.getFields();
        final int length = fields.length;
        if (length == 0) {
            return null;
        }
        final String[] names = new String[length];
        for (int i = 0; i < length; i += 1) {
            names[i] = fields[i].getName();
        }
        return names;
    }
    
    /**
     * Produce a string from a Number.
     *
     * @param number A Number
     *
     * @return A String.
     *
     * @throws JSONException If n is a non-finite number.
     */
    public static String numberToString(final Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(number);
        // Shave off trailing zeros and decimal point, if possible.
        String string = number.toString();
        if ((string.indexOf('.') > 0) && (string.indexOf('e') < 0) && (string.indexOf('E') < 0)) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }
    
    /**
     * Produce a string in double quotes with backslash sequences in all the right places. A backslash will be inserted
     * control character or an unescaped quote or backslash.
     *
     * @param string A String
     *
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(final String string) {
        final StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (final IOException ignored) {
                // will never happen - we are writing to a string writer
                return "";
            }
        }
    }
    
    public static Writer quote(final String string, final Writer w) throws IOException {
        if ((string == null) || (string.isEmpty())) {
            w.write("\"\"");
            return w;
        }
        char b;
        char c = 0;
        String hhhh;
        int i;
        final int len = string.length();
        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    w.write('\\');
                    w.write(c);
                    break;
                case '/':
                    if (b == '<') {
                        w.write('\\');
                    }
                    w.write(c);
                    break;
                case '\b':
                    w.write("\\b");
                    break;
                case '\t':
                    w.write("\\t");
                    break;
                case '\n':
                    w.write("\\n");
                    break;
                case '\f':
                    w.write("\\f");
                    break;
                case '\r':
                    w.write("\\r");
                    break;
                default:
                    if ((c < ' ') || ((c >= '\u0080') && (c < '\u00a0')) || ((c >= '\u2000') && (c < '\u2100'))) {
                        w.write("\\u");
                        hhhh = Integer.toHexString(c);
                        w.write("0000", 0, 4 - hhhh.length());
                        w.write(hhhh);
                    } else {
                        w.write(c);
                    }
            }
        }
        w.write('"');
        return w;
    }
    
    /**
     * Try to convert a string into a number, boolean, or null. If the string can't be converted, return the string.
     *
     * @param string A String.
     *
     * @return A simple JSON value.
     */
    public static Object stringToValue(final String string) {
        Double d;
        if (string.equals("")) {
            return string;
        }
        if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (string.equalsIgnoreCase("null")) {
            return JSONObject.NULL;
        }
        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */
        final char b = string.charAt(0);
        if (((b >= '0') && (b <= '9')) || (b == '-')) {
            try {
                if ((string.indexOf('.') > -1) || (string.indexOf('e') > -1) || (string.indexOf('E') > -1)) {
                    d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {
                        return d;
                    }
                } else {
                    final Long myLong = new Long(string);
                    if (string.equals(myLong.toString())) {
                        if (myLong == myLong.intValue()) {
                            return myLong.intValue();
                        } else {
                            return myLong;
                        }
                    }
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return string;
    }
    
    /**
     * Throw an exception if the object is a NaN or infinite number.
     *
     * @param o The object to test.
     *
     * @throws JSONException If o is a non-finite number.
     */
    public static void testValidity(final Object o) throws JSONException {
        if (o != null) {
            if (o instanceof Double) {
                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {
                    throw new JSONException("JSON does not allow non-finite numbers.");
                }
            } else if (o instanceof Float) {
                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {
                    throw new JSONException("JSON does not allow non-finite numbers.");
                }
            }
        }
    }
    
    /**
     * Make a JSON text of an Object value. If the object has an value.toJSONString() method, then that method will be
     * used to produce the JSON text. The method is required to produce a strictly conforming text. If the object does
     * not contain a toJSONString method (which is the most common case), then a text will be produced by other means.
     * If the value is an array or Collection, then a JSONArray will be made from it and its toJSONString method will be
     * called. If the value is a MAP, then a JSONObject will be made from it and its toJSONString method will be called.
     * Otherwise, the value's toString method will be called, and the result will be quoted.
     *
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value The value to be serialized.
     *
     * @return a printable, displayable, transmittable representation of the object, beginning with
     * <code>{</code>&nbsp;<small>(left brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     * brace)</small>.
     *
     * @throws JSONException If the value is or contains an invalid number.
     */
    public static String valueToString(final Object value) throws JSONException {
        if ((value == null) || value.equals(null)) {
            return "null";
        }
        if (value instanceof JSONString) {
            Object object;
            try {
                object = ((JSONString) value).toJSONString();
            } catch (final Exception e) {
                throw new JSONException(e);
            }
            if (object != null) {
                return (String) object;
            }
            throw new JSONException("Bad value from toJSONString: " + object);
        }
        if (value instanceof Number) {
            return numberToString((Number) value);
        }
        if ((value instanceof Boolean) || (value instanceof JSONObject) || (value instanceof JSONArray)) {
            return value.toString();
        }
        if (value instanceof Map) {
            return new JSONObject((Map<String, Object>) value).toString();
        }
        if (value instanceof Collection) {
            return new JSONArray((Collection<Object>) value).toString();
        }
        if (value.getClass().isArray()) {
            return new JSONArray(value).toString();
        }
        return quote(value.toString());
    }
    
    /**
     * Wrap an object, if necessary. If the object is null, return the NULL object. If it is an array or collection,
     * wrap it in a JSONArray. If it is a map, wrap it in a JSONObject. If it is a standard property (Double, String, et
     * al) then it is already wrapped. Otherwise, if it comes from one of the java packages, turn it into a string. And
     * if it doesn't, try to wrap it in a JSONObject. If the wrapping fails, then null is returned.
     *
     * @param object The object to wrap
     *
     * @return The wrapped value
     */
    public static Object wrap(final Object object) {
        try {
            if (object == null) {
                return NULL;
            }
            if ((object instanceof JSONObject)
                    || (object instanceof JSONArray)
                    || NULL.equals(object)
                    || (object instanceof JSONString)
                    || (object instanceof Byte)
                    || (object instanceof Character)
                    || (object instanceof Short)
                    || (object instanceof Integer)
                    || (object instanceof Long)
                    || (object instanceof Boolean)
                    || (object instanceof Float)
                    || (object instanceof Double)
                    || (object instanceof String)) {
                return object;
            }
            if (object instanceof Collection) {
                return new JSONArray((Collection<Object>) object);
            }
            if (object.getClass().isArray()) {
                return new JSONArray(object);
            }
            if (object instanceof Map) {
                return new JSONObject((Map<String, Object>) object);
            }
            final Package objectPackage = object.getClass().getPackage();
            final String objectPackageName = objectPackage != null ? objectPackage.getName() : "";
            if (objectPackageName.startsWith("java.") || objectPackageName.startsWith("javax.") || (object.getClass().getClassLoader() == null)) {
                return object.toString();
            }
            return new JSONObject(object);
        } catch (JSONException exception) {
            return null;
        }
    }
    
    static final Writer writeValue(final Writer writer, final Object value, final int indentFactor, final int indent) throws JSONException, IOException {
        if ((value == null) || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, indent);
        } else if (value instanceof Map) {
            new JSONObject((Map<String, Object>) value).write(writer, indentFactor, indent);
        } else if (value instanceof Collection) {
            new JSONArray((Collection<Object>) value).write(writer, indentFactor, indent);
        } else if (value.getClass().isArray()) {
            new JSONArray(value).write(writer, indentFactor, indent);
        } else if (value instanceof Number) {
            writer.write(numberToString((Number) value));
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof JSONString) {
            Object o;
            try {
                o = ((JSONString) value).toJSONString();
            } catch (final Exception e) {
                throw new JSONException(e);
            }
            writer.write(o != null ? o.toString() : quote(value.toString()));
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }
    
    static final void indent(final Writer writer, final int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.write(' ');
        }
    }
    
    /**
     * Accumulate values under a key. It is similar to the put method except that if there is already an object stored
     * under the key then a JSONArray is stored under the key to hold all of the accumulated values. If there is already
     * a JSONArray, then the new value is appended to it. In contrast, the put method replaces the previous value.
     *
     * If only one value is accumulated that is not a JSONArray, then the result will be the same as using put. But if
     * multiple values are accumulated, then the result will be like append.
     *
     * @param key   A key string.
     * @param value An object to be accumulated under the key.
     *
     * @return this.
     *
     * @throws JSONException If the value is an invalid number or if the key is null.
     */
    public JSONObject accumulate(final String key, final Object value) throws JSONException {
        testValidity(value);
        final Object object = opt(key);
        if (object == null) {
            this.put(key, value instanceof JSONArray ? new JSONArray().put(value) : value);
        } else if (object instanceof JSONArray) {
            ((JSONArray) object).put(value);
        } else {
            this.put(key, new JSONArray().put(object).put(value));
        }
        return this;
    }
    
    /**
     * Append values to the array under a key. If the key does not exist in the JSONObject, then the key is put in the
     * JSONObject with its value being a JSONArray containing the value parameter. If the key was already associated
     * with a JSONArray, then the value parameter is appended to it.
     *
     * @param key   A key string.
     * @param value An object to be accumulated under the key.
     *
     * @return this.
     *
     * @throws JSONException If the key is null or if the current value associated with the key is not a JSONArray.
     */
    public JSONObject append(final String key, final Object value) throws JSONException {
        testValidity(value);
        final Object object = opt(key);
        if (object == null) {
            this.put(key, new JSONArray().put(value));
        } else if (object instanceof JSONArray) {
            this.put(key, ((JSONArray) object).put(value));
        } else {
            throw new JSONException("JSONObject[" + key + "] is not a JSONArray.");
        }
        return this;
    }
    
    /**
     * Get the value object associated with a key.
     *
     * @param key A key string.
     *
     * @return The object associated with the key.
     *
     * @throws JSONException if the key is not found.
     */
    public Object get(final String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        final Object object = opt(key);
        if (object == null) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        }
        return object;
    }
    
    /**
     * Get the boolean value associated with a key.
     *
     * @param key A key string.
     *
     * @return The truth.
     *
     * @throws JSONException if the value is not a Boolean or the String "true" or "false".
     */
    public boolean getBoolean(final String key) throws JSONException {
        final Object object = get(key);
        if (object.equals(Boolean.FALSE) || ((object instanceof String) && ((String) object).equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE) || ((object instanceof String) && ((String) object).equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a Boolean.");
    }
    
    /**
     * Get the double value associated with a key.
     *
     * @param key A key string.
     *
     * @return The numeric value.
     *
     * @throws JSONException if the key is not found or if the value is not a Number object and cannot be converted to a
     *                       number.
     */
    public double getDouble(final String key) throws JSONException {
        final Object object = get(key);
        try {
            return object instanceof Number ? ((Number) object).doubleValue() : Double.parseDouble((String) object);
        } catch (NumberFormatException e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not a number.");
        }
    }
    
    /**
     * Get the int value associated with a key.
     *
     * @param key A key string.
     *
     * @return The integer value.
     *
     * @throws JSONException if the key is not found or if the value cannot be converted to an integer.
     */
    public int getInt(final String key) throws JSONException {
        final Object object = get(key);
        try {
            return object instanceof Number ? ((Number) object).intValue() : Integer.parseInt((String) object);
        } catch (NumberFormatException e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not an int.");
        }
    }
    
    /**
     * Get the JSONArray value associated with a key.
     *
     * @param key A key string.
     *
     * @return A JSONArray which is the value.
     *
     * @throws JSONException if the key is not found or if the value is not a JSONArray.
     */
    public JSONArray getJSONArray(final String key) throws JSONException {
        final Object object = get(key);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONArray.");
    }
    
    /**
     * Get the JSONObject value associated with a key.
     *
     * @param key A key string.
     *
     * @return A JSONObject which is the value.
     *
     * @throws JSONException if the key is not found or if the value is not a JSONObject.
     */
    public JSONObject getJSONObject(final String key) throws JSONException {
        final Object object = get(key);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
    }
    
    /**
     * Get the long value associated with a key.
     *
     * @param key A key string.
     *
     * @return The long value.
     *
     * @throws JSONException if the key is not found or if the value cannot be converted to a long.
     */
    public long getLong(final String key) throws JSONException {
        final Object object = get(key);
        try {
            return object instanceof Number ? ((Number) object).longValue() : Long.parseLong((String) object);
        } catch (NumberFormatException e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not a long.");
        }
    }
    
    /**
     * Get the string associated with a key.
     *
     * @param key A key string.
     *
     * @return A string which is the value.
     *
     * @throws JSONException if there is no string value for the key.
     */
    public String getString(final String key) throws JSONException {
        final Object object = get(key);
        if (object instanceof String) {
            return (String) object;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] not a string.");
    }
    
    /**
     * Determine if the JSONObject contains a specific key.
     *
     * @param key A key string.
     *
     * @return true if the key exists in the JSONObject.
     */
    public boolean has(final String key) {
        return map.containsKey(key);
    }
    
    /**
     * Increment a property of a JSONObject. If there is no such property, create one with a value of 1. If there is
     * such a property, and if it is an Integer, Long, Double, or Float, then add one to it.
     *
     * @param key A key string.
     *
     * @return this.
     *
     * @throws JSONException If there is already a property with this name that is not an Integer, Long, Double, or
     *                       Float.
     */
    public JSONObject increment(final String key) throws JSONException {
        final Object value = opt(key);
        if (value == null) {
            this.put(key, 1);
        } else if (value instanceof Integer) {
            this.put(key, (Integer) value + 1);
        } else if (value instanceof Long) {
            this.put(key, (Long) value + 1);
        } else if (value instanceof Double) {
            this.put(key, (Double) value + 1);
        } else if (value instanceof Float) {
            this.put(key, (Float) value + 1);
        } else {
            throw new JSONException("Unable to increment [" + quote(key) + "].");
        }
        return this;
    }
    
    /**
     * Determine if the value associated with the key is null or if there is no value.
     *
     * @param key A key string.
     *
     * @return true if there is no value associated with the key or if the value is the JSONObject.NULL object.
     */
    public boolean isNull(final String key) {
        return JSONObject.NULL.equals(opt(key));
    }
    
    /**
     * Get an enumeration of the keys of the JSONObject.
     *
     * @return An iterator of the keys.
     */
    public Iterator<String> keys() {
        return keySet().iterator();
    }
    
    /**
     * Get a set of keys of the JSONObject.
     *
     * @return A keySet.
     */
    public Set<String> keySet() {
        return map.keySet();
    }
    
    /**
     * Get the number of keys stored in the JSONObject.
     *
     * @return The number of keys in the JSONObject.
     */
    public int length() {
        return map.size();
    }
    
    /**
     * Produce a JSONArray containing the names of the elements of this JSONObject.
     *
     * @return A JSONArray containing the key strings, or null if the JSONObject is empty.
     */
    public JSONArray names() {
        final JSONArray ja = new JSONArray();
        final Iterator<String> keys = keys();
        while (keys.hasNext()) {
            ja.put(keys.next());
        }
        return ja.length() == 0 ? null : ja;
    }
    
    /**
     * Get an optional value associated with a key.
     *
     * @param key A key string.
     *
     * @return An object which is the value, or null if there is no value.
     */
    public Object opt(final String key) {
        return key == null ? null : map.get(key);
    }
    
    /**
     * Get an optional boolean associated with a key. It returns false if there is no such key, or if the value is not
     * Boolean.TRUE or the String "true".
     *
     * @param key A key string.
     *
     * @return The truth.
     */
    public boolean optBoolean(final String key) {
        return this.optBoolean(key, false);
    }
    
    /**
     * Get an optional boolean associated with a key. It returns the defaultValue if there is no such key, or if it is
     * not a Boolean or the String "true" or "false" (case insensitive).
     *
     * @param key          A key string.
     * @param defaultValue The default.
     *
     * @return The truth.
     */
    public boolean optBoolean(final String key, final boolean defaultValue) {
        try {
            return getBoolean(key);
        } catch (JSONException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get an optional double associated with a key, or NaN if there is no such key or if its value is not a number. If
     * the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key A string which is the key.
     *
     * @return An object which is the value.
     */
    public double optDouble(final String key) {
        return this.optDouble(key, Double.NaN);
    }
    
    /**
     * Get an optional double associated with a key, or the defaultValue if there is no such key or if its value is not
     * a number. If the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     *
     * @return An object which is the value.
     */
    public double optDouble(final String key, final double defaultValue) {
        try {
            return getDouble(key);
        } catch (JSONException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get an optional int value associated with a key, or zero if there is no such key or if the value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key A key string.
     *
     * @return An object which is the value.
     */
    public int optInt(final String key) {
        return this.optInt(key, 0);
    }
    
    /**
     * Get an optional int value associated with a key, or the default if there is no such key or if the value is not a
     * number. If the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     *
     * @return An object which is the value.
     */
    public int optInt(final String key, final int defaultValue) {
        try {
            return getInt(key);
        } catch (JSONException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get an optional JSONArray associated with a key. It returns null if there is no such key, or if its value is not
     * a JSONArray.
     *
     * @param key A key string.
     *
     * @return A JSONArray which is the value.
     */
    public JSONArray optJSONArray(final String key) {
        final Object o = opt(key);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }
    
    /**
     * Get an optional JSONObject associated with a key. It returns null if there is no such key, or if its value is not
     * a JSONObject.
     *
     * @param key A key string.
     *
     * @return A JSONObject which is the value.
     */
    public JSONObject optJSONObject(final String key) {
        final Object object = opt(key);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }
    
    /**
     * Get an optional long value associated with a key, or zero if there is no such key or if the value is not a
     * number. If the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key A key string.
     *
     * @return An object which is the value.
     */
    public long optLong(final String key) {
        return this.optLong(key, 0);
    }
    
    /**
     * Get an optional long value associated with a key, or the default if there is no such key or if the value is not a
     * number. If the value is a string, an attempt will be made to evaluate it as a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     *
     * @return An object which is the value.
     */
    public long optLong(final String key, final long defaultValue) {
        try {
            return getLong(key);
        } catch (JSONException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get an optional string associated with a key. It returns an empty string if there is no such key. If the value is
     * not a string and is not null, then it is converted to a string.
     *
     * @param key A key string.
     *
     * @return A string which is the value.
     */
    public String optString(final String key) {
        return this.optString(key, "");
    }
    
    /**
     * Get an optional string associated with a key. It returns the defaultValue if there is no such key.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     *
     * @return A string which is the value.
     */
    public String optString(final String key, final String defaultValue) {
        final Object object = opt(key);
        return NULL.equals(object) ? defaultValue : object.toString();
    }
    
    private void populateMap(final Object bean) {
        final Class klass = bean.getClass();
        // If klass is a System class then set includeSuperClass to false.
        final boolean includeSuperClass = klass.getClassLoader() != null;
        final Method[] methods = includeSuperClass ? klass.getMethods() : klass.getDeclaredMethods();
        for (final Method method : methods) {
            try {
                if (Modifier.isPublic(method.getModifiers())) {
                    final String name = method.getName();
                    String key = "";
                    if (name.startsWith("get")) {
                        if ("getClass".equals(name) || "getDeclaringClass".equals(name)) {
                            key = "";
                        } else {
                            key = name.substring(3);
                        }
                    } else if (name.startsWith("is")) {
                        key = name.substring(2);
                    }
                    if ((!key.isEmpty()) && Character.isUpperCase(key.charAt(0)) && (method.getParameterTypes().length == 0)) {
                        if (key.length() == 1) {
                            key = key.toLowerCase();
                        } else if (!Character.isUpperCase(key.charAt(1))) {
                            key = key.substring(0, 1).toLowerCase() + key.substring(1);
                        }
                        final Object result = method.invoke(bean, (Object[]) null);
                        if (result != null) {
                            map.put(key, wrap(result));
                        }
                    }
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ignore) {
            }
        }
    }
    
    /**
     * Put a key/boolean pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A boolean which is the value.
     *
     * @return this.
     *
     * @throws JSONException If the key is null.
     */
    public JSONObject put(final String key, final boolean value) throws JSONException {
        this.put(key, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }
    
    /**
     * Put a key/value pair in the JSONObject, where the value will be a JSONArray which is produced from a Collection.
     *
     * @param key   A key string.
     * @param value A Collection value.
     *
     * @return this.
     *
     * @throws JSONException
     */
    public JSONObject put(final String key, final Collection<Object> value) throws JSONException {
        this.put(key, new JSONArray(value));
        return this;
    }
    
    /**
     * Put a key/double pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A double which is the value.
     *
     * @return this.
     *
     * @throws JSONException If the key is null or if the number is invalid.
     */
    public JSONObject put(final String key, final double value) throws JSONException {
        this.put(key, new Double(value));
        return this;
    }
    
    /**
     * Put a key/int pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value An int which is the value.
     *
     * @return this.
     *
     * @throws JSONException If the key is null.
     */
    public JSONObject put(final String key, final int value) throws JSONException {
        this.put(key, new Integer(value));
        return this;
    }
    
    /**
     * Put a key/long pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A long which is the value.
     *
     * @return this.
     *
     * @throws JSONException If the key is null.
     */
    public JSONObject put(final String key, final long value) throws JSONException {
        this.put(key, new Long(value));
        return this;
    }
    
    /**
     * Put a key/value pair in the JSONObject, where the value will be a JSONObject which is produced from a Map.
     *
     * @param key   A key string.
     * @param value A Map value.
     *
     * @return this.
     *
     * @throws JSONException
     */
    public JSONObject put(final String key, final Map<String, Object> value) throws JSONException {
        this.put(key, new JSONObject(value));
        return this;
    }
    
    /**
     * Put a key/value pair in the JSONObject. If the value is null, then the key will be removed from the JSONObject if
     * it is present.
     *
     * @param key   A key string.
     * @param value An object which is the value. It should be of one of these types: Boolean, Double, Integer,
     *              JSONArray, JSONObject, Long, String, or the JSONObject.NULL object.
     *
     * @return this.
     *
     * @throws JSONException If the value is non-finite number or if the key is null.
     */
    public JSONObject put(final String key, final Object value) throws JSONException {
        if (key == null) {
            throw new NullPointerException("Null key.");
        }
        if (value != null) {
            testValidity(value);
            map.put(key, value);
        } else {
            remove(key);
        }
        return this;
    }
    
    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value are both non-null, and only if there is
     * not already a member with that name.
     *
     * @param key   string
     * @param value object
     *
     * @return this.
     *
     * @throws JSONException if the key is a duplicate
     */
    public JSONObject putOnce(final String key, final Object value) throws JSONException {
        if ((key != null) && (value != null)) {
            if (opt(key) != null) {
                throw new JSONException("Duplicate key \"" + key + "\"");
            }
            this.put(key, value);
        }
        return this;
    }
    
    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value are both non-null.
     *
     * @param key   A key string.
     * @param value An object which is the value. It should be of one of these types: Boolean, Double, Integer,
     *              JSONArray, JSONObject, Long, String, or the JSONObject.NULL object.
     *
     * @return this.
     *
     * @throws JSONException If the value is a non-finite number.
     */
    public JSONObject putOpt(final String key, final Object value) throws JSONException {
        if ((key != null) && (value != null)) {
            this.put(key, value);
        }
        return this;
    }
    
    /**
     * Remove a name and its value, if present.
     *
     * @param key The name to be removed.
     *
     * @return The value that was associated with the name, or null if there was no value.
     */
    public Object remove(final String key) {
        return map.remove(key);
    }
    
    /**
     * Determine if two JSONObjects are similar. They must contain the same set of names which must be associated with
     * similar values.
     *
     * @param other The other JSONObject
     *
     * @return true if they are equal
     */
    public boolean similar(final Object other) {
        try {
            if (!(other instanceof JSONObject)) {
                return false;
            }
            final Set<String> set = keySet();
            if (!set.equals(((JSONObject) other).keySet())) {
                return false;
            }
            for (final String name : set) {
                final Object valueThis = get(name);
                final Object valueOther = ((JSONObject) other).get(name);
                if (valueThis instanceof JSONObject) {
                    if (!((JSONObject) valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof JSONArray) {
                    if (!((JSONArray) valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (!valueThis.equals(valueOther)) {
                    return false;
                }
            }
            return true;
        } catch (final Throwable exception) {
            return false;
        }
    }
    
    /**
     * Produce a JSONArray containing the values of the members of this JSONObject.
     *
     * @param names A JSONArray containing a list of key strings. This determines the sequence of the values in the
     *              result.
     *
     * @return A JSONArray of values.
     *
     * @throws JSONException If any of the values are non-finite numbers.
     */
    public JSONArray toJSONArray(final JSONArray names) throws JSONException {
        if ((names == null) || (names.length() == 0)) {
            return null;
        }
        final JSONArray ja = new JSONArray();
        for (int i = 0; i < names.length(); i += 1) {
            ja.put(opt(names.getString(i)));
        }
        return ja;
    }
    
    /**
     * Make a JSON text of this JSONObject. For compactness, no whitespace is added. If this would not result in a
     * syntactically correct JSON text, then null will be returned instead.
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a printable, displayable, portable, transmittable representation of the object, beginning with
     * <code>{</code>&nbsp;<small>(left brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     * brace)</small>.
     */
    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (JSONException e) {
            return null;
        }
    }
    
    /**
     * Make a prettyprinted JSON text of this JSONObject.
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param indentFactor The number of spaces to add to each level of indentation.
     *
     * @return a printable, displayable, portable, transmittable representation of the object, beginning with
     * <code>{</code>&nbsp;<small>(left brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     * brace)</small>.
     *
     * @throws JSONException If the object contains an invalid number.
     */
    public String toString(final int indentFactor) throws JSONException {
        final StringWriter w = new StringWriter();
        synchronized (w.getBuffer()) {
            return this.write(w, indentFactor, 0).toString();
        }
    }
    
    /**
     * Write the contents of the JSONObject as JSON text to a writer. For compactness, no whitespace is added.
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return The writer.
     *
     * @throws JSONException
     */
    public Writer write(final Writer writer) throws JSONException {
        return this.write(writer, 0, 0);
    }
    
    /**
     * Write the contents of the JSONObject as JSON text to a writer. For compactness, no whitespace is added.
     *
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return The writer.
     *
     * @throws JSONException
     */
    Writer write(final Writer writer, final int indentFactor, final int indent) throws JSONException {
        try {
            boolean commanate = false;
            final int length = length();
            final Iterator<String> keys = keys();
            writer.write('{');
            if (length == 1) {
                final Object key = keys.next();
                writer.write(quote(key.toString()));
                writer.write(':');
                if (indentFactor > 0) {
                    writer.write(' ');
                }
                writeValue(writer, map.get(key), indentFactor, indent);
            } else if (length != 0) {
                final int newindent = indent + indentFactor;
                while (keys.hasNext()) {
                    final Object key = keys.next();
                    if (commanate) {
                        writer.write(',');
                    }
                    if (indentFactor > 0) {
                        writer.write('\n');
                    }
                    indent(writer, newindent);
                    writer.write(quote(key.toString()));
                    writer.write(':');
                    if (indentFactor > 0) {
                        writer.write(' ');
                    }
                    writeValue(writer, map.get(key), indentFactor, newindent);
                    commanate = true;
                }
                if (indentFactor > 0) {
                    writer.write('\n');
                }
                indent(writer, indent);
            }
            writer.write('}');
            return writer;
        } catch (final IOException exception) {
            throw new JSONException(exception);
        }
    }
    
    /**
     * JSONObject.NULL is equivalent to the value that JavaScript calls null, whilst Java's null is equivalent to the
     * value that JavaScript calls undefined.
     */
    private static final class Null {
        /**
         * There is only intended to be a single instance of the NULL object, so the clone method returns itself.
         *
         * @return NULL.
         */
        @Override
        protected final Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                return this;
            }
        }
        
        /**
         * A Null object is equal to the null value and to itself.
         *
         * @param object An object to test for nullness.
         *
         * @return true if the object parameter is the JSONObject.NULL object or null.
         */
        @Override
        public boolean equals(final Object object) {
            return (object == null) || (object == this);
        }
        
        /**
         * Get the "null" string value.
         *
         * @return The string "null".
         */
        @Override
        public String toString() {
            return "null";
        }
    }
}
