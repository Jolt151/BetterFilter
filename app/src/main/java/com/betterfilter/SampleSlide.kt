package com.betterfilter

import android.os.Bundle
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment


class SampleSlide : Fragment() {
    private var layoutResId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getArguments()?.containsKey(ARG_LAYOUT_RES_ID) == true) {
            layoutResId = getArguments()!!.getInt(ARG_LAYOUT_RES_ID)
        }
    }

    @Nullable
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(layoutResId, container, false)
    }

    companion object {

        private val ARG_LAYOUT_RES_ID = "layoutResId"

        fun newInstance(layoutResId: Int): SampleSlide {
            val sampleSlide = SampleSlide()

            val args = Bundle()
            args.putInt(ARG_LAYOUT_RES_ID, layoutResId)
            sampleSlide.setArguments(args)

            return sampleSlide
        }
    }
}
