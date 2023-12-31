/*
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.superblaubeere27.asmdelta.utils.typeadapter;

import com.google.gson.*;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;

import java.lang.reflect.Type;

public class AbstractDifferenceSerializer implements JsonSerializer<AbstractDifference>, JsonDeserializer<AbstractDifference> {

    private static final String CLASS_META_KEY = "CLASS_META_KEY";

    @Override
    public AbstractDifference deserialize(JsonElement jsonElement, Type type,
                                          JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        JsonObject jsonObj = jsonElement.getAsJsonObject();
        String className = jsonObj.get(CLASS_META_KEY).getAsString();
        if (className.equals(MethodLocalVariableDifferenceSerializer.class.getCanonicalName())) {
            return (new MethodLocalVariableDifferenceSerializer()).deserialize(jsonElement, type, jsonDeserializationContext);
        }
        try {
            Class<?> clz = Class.forName(className);
            return jsonDeserializationContext.deserialize(jsonElement, clz);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException(e);
        }
    }

    @Override
    public JsonElement serialize(AbstractDifference object, Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        JsonElement jsonEle = jsonSerializationContext.serialize(object, object.getClass());
        jsonEle.getAsJsonObject().addProperty(CLASS_META_KEY,
                object.getClass().getCanonicalName());
        return jsonEle;
    }

}
