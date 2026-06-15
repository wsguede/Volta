package com.volta.app.data.export

import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

class ExportRepository
@Inject
constructor(
    @Named("IO") private val dispatcher: CoroutineDispatcher,
) {
    // TODO: Write equirectangular JPEG with GPano XMP + GPS EXIF to MediaStore
}
